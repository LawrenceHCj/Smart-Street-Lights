const { nowIso } = require("../utils/date");

function createDevice(id, name, location, binding, timestamp = nowIso()) {
  return {
    id,
    name,
    location,
    binding,
    bound: true,
    online: true,
    lampStatus: "OFF",
    lastLux: 0,
    lastSeenAt: timestamp,
    updatedAt: timestamp,
  };
}

module.exports = {
  createDevice,
};
