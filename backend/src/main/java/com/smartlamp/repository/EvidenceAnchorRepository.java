package com.smartlamp.repository;

import com.smartlamp.entity.EvidenceAnchor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvidenceAnchorRepository extends JpaRepository<EvidenceAnchor, Long> {
    Optional<EvidenceAnchor> findFirstByDeviceCodeOrderBySeqDesc(String deviceCode);
}
