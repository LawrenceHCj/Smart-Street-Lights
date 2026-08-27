package com.smartlamp.repository;

import com.smartlamp.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
}
