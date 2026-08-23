const { sendJson, readBody } = require("../utils/http");
const { clamp } = require("../utils/number");
const { getDevice, createNewDevice, updateDevice } = require("../services/deviceService");
const { sendControlCommand } = require("../services/controlService");
const { updateConfig } = require("../services/configService");
const { setScenario } = require("../simulator/deviceSimulator");
const { answerMaintenanceQuestion } = require("../agent/assistantService");

function ok(res, data = null, statusCode = 200) {
  return sendJson(res, statusCode, {
    code: 0,
    message: "ok",
    data,
  });
}

function fail(res, statusCode, message) {
  return sendJson(res, statusCode, {
    code: statusCode,
    message,
    data: null,
  });
}

function toTimestamp(value) {
  const timestamp = Date.parse(value || "");
  return Number.isFinite(timestamp) ? timestamp : null;
}

function toDeviceVO(device) {
  return {
    id: device.id,
    code: device.id,
    location: device.location,
    status: device.online ? "ONLINE" : "OFFLINE",
    latestLux: device.lastLux ?? null,
    lastSeen: toTimestamp(device.lastSeenAt),
  };
}

function toAlarmVO(alert) {
  const levelMap = {
    INFO: "info",
    WARN: "warning",
    WARNING: "warning",
    CRITICAL: "critical",
  };

  return {
    id: alert.id,
    deviceId: alert.deviceId,
    type: alert.type,
    level: levelMap[String(alert.severity || "").toUpperCase()] || "warning",
    message: alert.message,
    ts: toTimestamp(alert.createdAt),
    status: alert.status === "ACKNOWLEDGED" ? "ACKED" : "OPEN",
  };
}

function buildOverview(state) {
  const totalDevices = state.devices.length;
  const onlineCount = state.devices.filter((device) => device.online).length;
  const luxValues = state.devices
    .map((device) => Number(device.lastLux))
    .filter((lux) => Number.isFinite(lux));

  const avgLux = luxValues.length
    ? Math.round(luxValues.reduce((sum, lux) => sum + lux, 0) / luxValues.length)
    : 0;

  return {
    totalDevices,
    onlineCount,
    offlineCount: totalDevices - onlineCount,
    avgLux,
  };
}

function buildHistory(state, deviceId, start, end) {
  const startMs = Number(start || 0);
  const endMs = Number(end || Date.now());
  const points = state.telemetry
    .filter((row) => row.deviceId === deviceId)
    .map((row) => ({
      ts: toTimestamp(row.timestamp),
      lux: row.lux,
    }))
    .filter((point) => point.ts !== null && point.ts >= startMs && point.ts <= endMs)
    .slice(-240);

  return { deviceId, points };
}

function getLatestLight(state, device) {
  const latest = state.telemetry
    .filter((row) => row.deviceId === device.id)
    .slice(-1)[0];

  return {
    deviceId: device.id,
    lux: latest ? latest.lux : device.lastLux ?? null,
    ts: latest ? toTimestamp(latest.timestamp) : toTimestamp(device.lastSeenAt),
  };
}

function normalizeSources(sources) {
  return (sources || []).map((source, index) => {
    if (typeof source === "string") {
      return {
        title: source,
        section: "知识库",
        score: Number((1 - index * 0.08).toFixed(2)),
      };
    }
    return source;
  });
}

function createApiRouter({ store, sseHub }) {
  function changed(payload, statusCode = 200) {
    store.scheduleSave();
    sseHub.broadcast(store.getSummary());
    return { payload, statusCode };
  }

  async function handle(req, res, url) {
    const state = store.getState();
    const method = req.method || "GET";
    const pathname = url.pathname;

    try {
      if (method === "POST" && pathname === "/api/auth/login") {
        const body = await readBody(req);
        if (body.username === "admin" && body.password === "123456") {
          return ok(res, {
            token: "dev-admin-token",
            username: "admin",
            role: "admin",
          });
        }
        return fail(res, 401, "用户名或密码错误");
      }

      if (method === "GET" && pathname === "/api/summary") return ok(res, store.getSummary());
      if (method === "GET" && pathname === "/api/dashboard/overview") return ok(res, buildOverview(state));
      if (method === "GET" && pathname === "/api/config") return ok(res, state.config);
      if (method === "GET" && pathname === "/api/config/linkage") {
        return ok(res, {
          enabled: Boolean(state.config.autoControl),
          threshold: Number(state.config.luxThreshold),
        });
      }
      if (method === "GET" && pathname === "/api/devices") return ok(res, state.devices.map(toDeviceVO));
      if (method === "GET" && (pathname === "/api/alerts" || pathname === "/api/alarms")) {
        return ok(res, state.alerts.slice().reverse().map(toAlarmVO));
      }
      if (method === "GET" && pathname === "/api/users") {
        return ok(res, [
          {
            id: 1,
            username: "admin",
            role: "admin",
            status: "ENABLED",
            createdAt: Date.now(),
          },
        ]);
      }

      if (method === "GET" && pathname === "/api/telemetry") {
        const deviceId = url.searchParams.get("deviceId");
        const limit = clamp(Number(url.searchParams.get("limit") || 120), 1, 500);
        const rows = state.telemetry.filter((row) => !deviceId || row.deviceId === deviceId).slice(-limit);
        return ok(res, rows);
      }

      if (method === "GET" && pathname === "/api/light/history") {
        return ok(
          res,
          buildHistory(
            state,
            url.searchParams.get("deviceId"),
            url.searchParams.get("start"),
            url.searchParams.get("end"),
          ),
        );
      }

      if (method === "PUT" && pathname === "/api/config") {
        const result = changed(updateConfig(state, await readBody(req)));
        return ok(res, result.payload, result.statusCode);
      }

      if (method === "PUT" && pathname === "/api/config/linkage") {
        const body = await readBody(req);
        const result = changed(updateConfig(state, {
          ...state.config,
          autoControl: body.enabled,
          luxThreshold: body.threshold,
        }));
        return ok(res, result.payload, result.statusCode);
      }

      if (method === "POST" && pathname === "/api/devices") {
        const body = await readBody(req);
        const result = changed(createNewDevice(state, {
          id: body.id || body.code,
          name: body.name || body.code,
          location: body.location,
          binding: body.binding || "",
        }), 201);
        return ok(res, toDeviceVO(result.payload), result.statusCode);
      }

      const deviceMatch = pathname.match(/^\/api\/devices\/([^/]+)$/);
      if (deviceMatch && method === "PATCH") {
        const result = changed(updateDevice(state, decodeURIComponent(deviceMatch[1]), await readBody(req)));
        return ok(res, toDeviceVO(result.payload), result.statusCode);
      }
      if (deviceMatch && method === "DELETE") {
        const device = getDevice(state, decodeURIComponent(deviceMatch[1]));
        if (!device) return fail(res, 404, "device not found");
        const result = changed(updateDevice(state, device.id, { bound: false }));
        return ok(res, toDeviceVO(result.payload), result.statusCode);
      }

      const lightMatch = pathname.match(/^\/api\/devices\/([^/]+)\/light$/);
      if (lightMatch && method === "GET") {
        const device = getDevice(state, decodeURIComponent(lightMatch[1]));
        if (!device) return fail(res, 404, "device not found");
        return ok(res, getLatestLight(state, device));
      }

      const controlMatch = pathname.match(/^\/api\/devices\/([^/]+)\/control$/);
      if (controlMatch && method === "POST") {
        const device = getDevice(state, decodeURIComponent(controlMatch[1]));
        if (!device) return fail(res, 404, "device not found");
        const body = await readBody(req);
        const result = changed(sendControlCommand(state, device, String(body.action || "").toUpperCase(), "MANUAL"));
        return ok(res, result.payload, result.statusCode);
      }

      const switchMatch = pathname.match(/^\/api\/devices\/([^/]+)\/switch$/);
      if (switchMatch && method === "POST") {
        const device = getDevice(state, decodeURIComponent(switchMatch[1]));
        if (!device) return fail(res, 404, "device not found");
        const body = await readBody(req);
        const result = changed(sendControlCommand(state, device, body.on ? "ON" : "OFF", "MANUAL"));
        return ok(res, null, result.statusCode);
      }

      const ackMatch = pathname.match(/^\/api\/alarms\/([^/]+)\/ack$/);
      if (ackMatch && method === "POST") {
        const alert = state.alerts.find((item) => item.id === decodeURIComponent(ackMatch[1]));
        if (!alert) return fail(res, 404, "alarm not found");
        alert.status = "ACKNOWLEDGED";
        changed(alert);
        return ok(res, null);
      }

      if (method === "POST" && pathname === "/api/simulator/scenario") {
        const body = await readBody(req);
        const result = changed(setScenario(state, String(body.scenario || "normal")));
        return ok(res, result.payload, result.statusCode);
      }

      if (method === "POST" && pathname === "/api/assistant/chat") {
        const body = await readBody(req);
        return ok(res, await answerMaintenanceQuestion(body.question));
      }

      if (method === "POST" && pathname === "/api/agent/ask") {
        const body = await readBody(req);
        const answer = await answerMaintenanceQuestion(body.question);
        return ok(res, {
          answer: answer.answer,
          sources: normalizeSources(answer.sources),
        });
      }

      if (["POST", "PUT", "DELETE"].includes(method) && pathname.startsWith("/api/users")) {
        return ok(res, null);
      }

      return fail(res, 404, "api route not found");
    } catch (error) {
      return fail(res, error.statusCode || 400, error.message);
    }
  }

  return {
    handle,
    sendError(res, statusCode, message) {
      return fail(res, statusCode, message);
    },
  };
}

module.exports = {
  createApiRouter,
};
