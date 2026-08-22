const { randomUUID } = require("crypto");
const { nowIso } = require("../utils/date");
const { recoverDeviceAlert } = require("./alertService");
const { applyAutoControl } = require("./controlService");

function recordTelemetry(state, device, lux, source = "SIMULATOR") {
  const timestamp = nowIso();
  device.lastLux = lux;
  device.lastSeenAt = timestamp;
  device.updatedAt = timestamp;

  if (!device.online) {
    device.online = true;
    recoverDeviceAlert(state, device);
  }

  state.telemetry.push({
    id: randomUUID(),
    deviceId: device.id,
    lux,
    lampStatus: device.lampStatus,
    timestamp,
    source,
  });

  state.telemetry = state.telemetry.slice(-1000);
  applyAutoControl(state, device, lux);
}

module.exports = {
  recordTelemetry,
};
