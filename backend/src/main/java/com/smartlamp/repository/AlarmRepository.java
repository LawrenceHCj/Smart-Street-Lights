package com.smartlamp.repository;

import com.smartlamp.entity.Alarm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {
    List<Alarm> findAllByOrderByTsDesc();
    long countByStatus(String status);
}
