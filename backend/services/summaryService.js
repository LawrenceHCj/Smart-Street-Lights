const { nowIso } = require("../utils/date");

function latestTelemetry(state, deviceId) {
  const rows = state.telemetry.filter((row) => row.deviceId === deviceId);
  return rows[rows.length - 1] || null;
}

function buildSummary(state) {
  const activeAlerts = state.alerts.filter((alert) => alert.status === "ACTIVE");
  const latestLux = state.telemetry.length ? state.telemetry[state.telemetry.length - 1].lux : null;

  return {
    timestamp: nowIso(),
    config: state.config,
    simulator: state.simulator,
    metrics: {
      totalDevices: state.devices.length,
      onlineDevices: state.devices.filter((device) => device.online).length,
      lampsOn: state.devices.filter((device) => device.lampStatus === "ON").length,
      activeAlerts: activeAlerts.length,
      latestLux,
    },
    devices: state.devices.map((device) => ({
      ...device,
      latestTelemetry: latestTelemetry(state, device.id),
    })),
    telemetry: state.telemetry.slice(-120),
    alerts: state.alerts.slice(-30).reverse(),
    controlLogs: state.controlLogs.slice(-20).reverse(),
  };
}

module.exports = {
  buildSummary,
  latestTelemetry,
};
