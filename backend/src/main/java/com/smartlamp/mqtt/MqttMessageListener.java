package com.smartlamp.mqtt;

import com.smartlamp.service.MqttDeadLetterService;
import com.smartlamp.service.MqttIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MqttMessageListener {
    private static final Logger log = LoggerFactory.getLogger(MqttMessageListener.class);

    private final MqttIngestionService ingestionService;
    private final MqttDeadLetterService deadLetterService;

    public MqttMessageListener(MqttIngestionService ingestionService, MqttDeadLetterService deadLetterService) {
        this.ingestionService = ingestionService;
        this.deadLetterService = deadLetterService;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload = String.valueOf(message.getPayload());
        try {
            ingestionService.ingest(topic, payload);
            log.debug("MQTT message persisted: topic={}", topic);
        } catch (Exception error) {
            log.warn("MQTT message rejected and stored as dead letter: topic={}, reason={}", topic, error.getMessage());
            try {
                deadLetterService.record(topic, payload, error);
            } catch (Exception deadLetterError) {
                log.error("Unable to persist MQTT dead letter: topic={}", topic, deadLetterError);
            }
        }
    }
}
