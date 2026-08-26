package com.smartlamp.repository;

import com.smartlamp.entity.LightPoint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LightPointRepository extends JpaRepository<LightPoint, Long> {

    List<LightPoint> findByDeviceCodeAndTsBetweenOrderByTsAsc(String deviceCode, Long start, Long end);

    List<LightPoint> findByDeviceCodeOrderByTsDesc(String deviceCode, Pageable pageable);

    List<LightPoint> findAllByOrderByTsDesc(Pageable pageable);   // 新增

    void deleteByDeviceCode(String deviceCode);
}