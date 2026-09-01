package com.smartlamp.repository;

import com.smartlamp.entity.DeviceCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import com.smartlamp.entity.enums.CommandStatus;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {
    Optional<DeviceCommand> findByCommandId(String commandId);
    Optional<DeviceCommand> findFirstByDeviceCodeAndStatusOrderByUpdatedAtDesc(String deviceCode, CommandStatus status);
    List<DeviceCommand> findByStatusAndCreatedAtBefore(CommandStatus status, LocalDateTime cutoff);
    void deleteByDeviceCode(String deviceCode);
}
