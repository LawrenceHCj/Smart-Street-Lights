const { randomUUID } = require("crypto");
const { nowIso } = require("../utils/date");

function activeOfflineAlert(state, deviceId) {
  return state.alerts.find(
    (alert) => alert.deviceId === deviceId && alert.type === "OFFLINE" && alert.status === "ACTIVE",
  );
}

function recoverDeviceAlert(state, device) {
  const alert = activeOfflineAlert(state, device.id);
  if (!alert) return null;

  alert.status = "RECOVERED";
  alert.recoveredAt = nowIso();
  alert.message = `${device.name} 心跳恢复，设备已重新上线。`;
  return alert;
}

function detectOfflineDevices(state) {
  const now = Date.now();
  let changed = false;

  for (const device of state.devices) {
    if (!device.bound) continue;

    const lastSeen = Date.parse(device.lastSeenAt || 0);
    const timedOut = Number.isFinite(lastSeen) && now - lastSeen > state.config.heartbeatTimeoutMs;

    if (timedOut && device.online) {
      device.online = false;
      device.updatedAt = nowIso();
      changed = true;

      if (!activeOfflineAlert(state, device.id)) {
        state.alerts.push({
          id: randomUUID(),
          deviceId: device.id,
          type: "OFFLINE",
          severity: "HIGH",
          status: "ACTIVE",
          message: `${device.name} 超过 ${Math.round(state.config.heartbeatTimeoutMs / 1000)} 秒未收到心跳。`,
          createdAt: nowIso(),
        });
      }
    }
  }

  state.alerts = state.alerts.slice(-200);
  return changed;
}

module.exports = {
  activeOfflineAlert,
  recoverDeviceAlert,
  detectOfflineDevices,
};
