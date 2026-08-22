const MQTT_TOPICS = {
  telemetry: "street-light/+/telemetry",
  heartbeat: "street-light/+/heartbeat",
  command: "street-light/{deviceId}/command",
  commandReply: "street-light/{deviceId}/command-reply",
};

function commandTopic(deviceId) {
  return MQTT_TOPICS.command.replace("{deviceId}", deviceId);
}

module.exports = {
  MQTT_TOPICS,
  commandTopic,
};
