const { clamp } = require("../utils/number");

function updateConfig(state, body) {
  state.config = {
    ...state.config,
    autoControl: Boolean(body.autoControl),
    luxThreshold: clamp(Number(body.luxThreshold), 10, 500),
    hysteresis: clamp(Number(body.hysteresis), 0, 200),
    heartbeatTimeoutMs: clamp(Number(body.heartbeatTimeoutMs), 5000, 120000),
  };
  return state.config;
}

module.exports = {
  updateConfig,
};
