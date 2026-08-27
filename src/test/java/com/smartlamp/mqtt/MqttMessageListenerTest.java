package com.smartlamp.mqtt;

import com.smartlamp.service.MqttDeadLetterService;
import com.smartlamp.service.MqttIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttMessageListenerTest {
    @Mock
    private MqttIngestionService ingestionService;
    @Mock
    private MqttDeadLetterService deadLetterService;

    @Test
    void recordsRejectedMessageAsDeadLetter() throws Exception {
        MqttMessageListener listener = new MqttMessageListener(ingestionService, deadLetterService);
        IllegalArgumentException error = new IllegalArgumentException("bad payload");
        doThrow(error).when(ingestionService).ingest("device/SL-001/data", "{}");

        listener.handleMessage(MessageBuilder.withPayload("{}")
                .setHeader(MqttHeaders.RECEIVED_TOPIC, "device/SL-001/data")
                .build());

        verify(deadLetterService).record("device/SL-001/data", "{}", error);
    }

    @Test
    void successfulMessageDoesNotCreateDeadLetter() throws Exception {
        MqttMessageListener listener = new MqttMessageListener(ingestionService, deadLetterService);

        listener.handleMessage(MessageBuilder.withPayload("{\"lux\":80}")
                .setHeader(MqttHeaders.RECEIVED_TOPIC, "device/SL-001/data")
                .build());

        verify(ingestionService).ingest("device/SL-001/data", "{\"lux\":80}");
        verifyNoInteractions(deadLetterService);
    }
}
