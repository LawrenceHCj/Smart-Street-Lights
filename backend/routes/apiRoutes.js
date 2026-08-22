const { sendJson, readBody } = require("../utils/http");
const { clamp } = require("../utils/number");
const { getDevice, createNewDevice, updateDevice } = require("../services/deviceService");
const { sendControlCommand } = require("../services/controlService");
const { updateConfig } = require("../services/configService");
const { setScenario } = require("../simulator/deviceSimulator");
const { answerMaintenanceQuestion } = require("../agent/assistantService");

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
      if (method === "GET" && pathname === "/api/summary") return sendJson(res, 200, store.getSummary());
      if (method === "GET" && pathname === "/api/config") return sendJson(res, 200, state.config);
      if (method === "GET" && pathname === "/api/devices") return sendJson(res, 200, state.devices);
      if (method === "GET" && pathname === "/api/alerts") return sendJson(res, 200, state.alerts.slice().reverse());

      if (method === "GET" && pathname === "/api/telemetry") {
        const deviceId = url.searchParams.get("deviceId");
        const limit = clamp(Number(url.searchParams.get("limit") || 120), 1, 500);
        const rows = state.telemetry.filter((row) => !deviceId || row.deviceId === deviceId).slice(-limit);
        return sendJson(res, 200, rows);
      }

      if (method === "PUT" && pathname === "/api/config") {
        const result = changed(updateConfig(state, await readBody(req)));
        return sendJson(res, result.statusCode, result.payload);
      }

      if (method === "POST" && pathname === "/api/devices") {
        const result = changed(createNewDevice(state, await readBody(req)), 201);
        return sendJson(res, result.statusCode, result.payload);
      }

      const deviceMatch = pathname.match(/^\/api\/devices\/([^/]+)$/);
      if (deviceMatch && method === "PATCH") {
        const result = changed(updateDevice(state, decodeURIComponent(deviceMatch[1]), await readBody(req)));
        return sendJson(res, result.statusCode, result.payload);
      }

      const controlMatch = pathname.match(/^\/api\/devices\/([^/]+)\/control$/);
      if (controlMatch && method === "POST") {
        const device = getDevice(state, decodeURIComponent(controlMatch[1]));
        if (!device) return sendJson(res, 404, { error: "device not found" });
        const body = await readBody(req);
        const result = changed(sendControlCommand(state, device, String(body.action || "").toUpperCase(), "MANUAL"));
        return sendJson(res, result.statusCode, result.payload);
      }

      if (method === "POST" && pathname === "/api/simulator/scenario") {
        const body = await readBody(req);
        const result = changed(setScenario(state, String(body.scenario || "normal")));
        return sendJson(res, result.statusCode, result.payload);
      }

      if (method === "POST" && pathname === "/api/assistant/chat") {
        const body = await readBody(req);
        return sendJson(res, 200, answerMaintenanceQuestion(body.question));
      }

      return sendJson(res, 404, { error: "api route not found" });
    } catch (error) {
      return sendJson(res, error.statusCode || 400, { error: error.message });
    }
  }

  return {
    handle,
    sendError(res, statusCode, message) {
      return sendJson(res, statusCode, { error: message });
    },
  };
}

module.exports = {
  createApiRouter,
};
