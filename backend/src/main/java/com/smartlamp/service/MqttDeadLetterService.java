package com.smartlamp.service;

import com.smartlamp.entity.MqttDeadLetter;
import com.smartlamp.repository.MqttDeadLetterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MqttDeadLetterService {
    private final MqttDeadLetterRepository repository;

    public MqttDeadLetterService(MqttDeadLetterRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String topic, String payload, Exception error) {
        MqttDeadLetter deadLetter = new MqttDeadLetter();
        deadLetter.setTopic(truncate(topic == null ? "<missing>" : topic, 255));
        deadLetter.setPayload(truncate(payload == null ? "<missing>" : payload, 65_535));
        deadLetter.setErrorMessage(truncate(error.getClass().getSimpleName() + ": " + error.getMessage(), 1000));
        deadLetter.setReceivedAt(LocalDateTime.now());
        repository.save(deadLetter);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
