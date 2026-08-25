package com.smartlamp.repository;

import com.smartlamp.entity.MqttDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MqttDeadLetterRepository extends JpaRepository<MqttDeadLetter, Long> {
}
