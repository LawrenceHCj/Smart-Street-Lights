const { createDevice } = require("../models/device");
const { nowIso } = require("../utils/date");

function getDevice(state, id) {
  return state.devices.find((device) => device.id === id);
}

function createNewDevice(state, body) {
  const id = String(body.id || `SL-${String(state.devices.length + 1).padStart(3, "0")}`).trim();
  if (getDevice(state, id)) {
    const error = new Error("device id already exists");
    error.statusCode = 409;
    throw error;
  }

  const device = createDevice(
    id,
    String(body.name || id).trim(),
    String(body.location || "未设置位置").trim(),
    String(body.binding || "").trim(),
  );
  state.devices.push(device);
  return device;
}

function updateDevice(state, id, body) {
  const device = getDevice(state, id);
  if (!device) {
    const error = new Error("device not found");
    error.statusCode = 404;
    throw error;
  }

  for (const key of ["name", "location", "binding"]) {
    if (body[key] !== undefined) device[key] = String(body[key]).trim();
  }
  if (body.bound !== undefined) device.bound = Boolean(body.bound);
  device.updatedAt = nowIso();
  return device;
}

module.exports = {
  getDevice,
  createNewDevice,
  updateDevice,
};
