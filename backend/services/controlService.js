const { randomUUID } = require("crypto");
const { nowIso } = require("../utils/date");

function sendControlCommand(state, device, action, mode = "MANUAL") {
  if (!["ON", "OFF"].includes(action)) throw new Error("action must be ON or OFF");
  if (!device.bound) throw new Error("device is unbound");

  const timestamp = nowIso();
  const command = {
    id: randomUUID(),
    commandId: `CMD-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
    deviceId: device.id,
    action,
    mode,
    status: "SUCCESS",
    issuedAt: timestamp,
    completedAt: timestamp,
    message: `${mode === "AUTO" ? "自动" : "手动"}${action === "ON" ? "开灯" : "关灯"}已执行`,
  };

  device.lampStatus = action;
  device.updatedAt = timestamp;
  state.controlLogs.push(command);
  state.controlLogs = state.controlLogs.slice(-200);
  return command;
}

function applyAutoControl(state, device, lux) {
  if (!state.config.autoControl || !device.online || !device.bound) return null;

  const turnOnLux = state.config.luxThreshold;
  const turnOffLux = state.config.luxThreshold + state.config.hysteresis;

  if (lux < turnOnLux && device.lampStatus !== "ON") {
    return sendControlCommand(state, device, "ON", "AUTO");
  }

  if (lux > turnOffLux && device.lampStatus !== "OFF") {
    return sendControlCommand(state, device, "OFF", "AUTO");
  }

  return null;
}

module.exports = {
  sendControlCommand,
  applyAutoControl,
};
