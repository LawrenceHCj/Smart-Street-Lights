package com.smartlamp.repository;

import com.smartlamp.entity.DataIntegrityEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataIntegrityEntryRepository extends JpaRepository<DataIntegrityEntry, Long> {
    List<DataIntegrityEntry> findByDeviceCodeAndSequenceNoGreaterThanOrderBySequenceNoAsc(
            String deviceCode, long sequenceNo, Pageable pageable);

    boolean existsByDeviceCode(String deviceCode);
}
