package com.smartlamp.repository;

import com.smartlamp.entity.DeviceCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {
    Optional<DeviceCommand> findByCommandId(String commandId);
}