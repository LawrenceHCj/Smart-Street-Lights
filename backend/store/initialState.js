const { nowIso } = require("../utils/date");
const { createDevice } = require("../models/device");

function createInitialState() {
  const createdAt = nowIso();

  return {
    config: {
      autoControl: true,
      luxThreshold: 120,
      hysteresis: 35,
      heartbeatTimeoutMs: 15000,
      simulatorIntervalMs: 2000,
    },
    simulator: {
      scenario: "normal",
      tick: 0,
      pausedDeviceIds: [],
    },
    devices: [
      createDevice("SL-001", "北门主路 01", "北门主路", "NB-GW-01", createdAt),
      createDevice("SL-002", "图书馆东侧 02", "图书馆东侧", "LIB-GW-02", createdAt),
      createDevice("SL-003", "实验楼南侧 03", "实验楼南侧", "LAB-GW-03", createdAt),
    ],
    telemetry: [],
    controlLogs: [],
    alerts: [],
  };
}

module.exports = {
  createInitialState,
};
