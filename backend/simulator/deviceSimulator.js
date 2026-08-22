const { clamp } = require("../utils/number");
const { recordTelemetry } = require("../services/telemetryService");
const { detectOfflineDevices } = require("../services/alertService");

function startDeviceSimulator({ store, sseHub }) {
  const state = store.getState();

  setInterval(() => {
    simulatorTick(state);
    store.scheduleSave();
    sseHub.broadcast(store.getSummary());
  }, state.config.simulatorIntervalMs);

  setInterval(() => {
    const changed = detectOfflineDevices(state);
    if (changed) {
      store.scheduleSave();
      sseHub.broadcast(store.getSummary());
    }
  }, 3000);
}

function simulatorTick(state) {
  state.simulator.tick += 1;

  state.devices.forEach((device, index) => {
    if (!device.bound) return;
    if (state.simulator.pausedDeviceIds.includes(device.id)) return;

    recordTelemetry(state, device, scenarioLux(state, index, state.simulator.tick));
  });
}

function scenarioLux(state, deviceIndex, tick) {
  const wave = Math.sin((tick + deviceIndex * 2) / 6);
  const noise = Math.round((Math.random() - 0.5) * 18);

  if (state.simulator.scenario === "low-light") return clamp(Math.round(72 + wave * 18 + noise), 12, 120);
  if (state.simulator.scenario === "daylight") return clamp(Math.round(235 + wave * 32 + noise), 170, 320);
  if (state.simulator.scenario === "outage") return clamp(Math.round(132 + wave * 45 + noise), 55, 240);
  return clamp(Math.round(155 + wave * 70 + noise), 35, 280);
}

function setScenario(state, scenario) {
  const allowed = ["normal", "low-light", "daylight", "outage"];
  if (!allowed.includes(scenario)) {
    throw new Error(`scenario must be one of: ${allowed.join(", ")}`);
  }

  state.simulator.scenario = scenario;
  state.simulator.pausedDeviceIds = scenario === "outage" && state.devices[0] ? [state.devices[0].id] : [];
  return state.simulator;
}

module.exports = {
  startDeviceSimulator,
  setScenario,
};
