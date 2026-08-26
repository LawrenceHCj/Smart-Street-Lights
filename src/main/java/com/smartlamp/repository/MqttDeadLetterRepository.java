package com.smartlamp.repository;

import com.smartlamp.entity.MqttDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface MqttDeadLetterRepository extends JpaRepository<MqttDeadLetter, Long> {
    void deleteByReceivedAtBefore(LocalDateTime time);
}