package com.mnk.identipatia.guidance.repository;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.guidance.model.ProtectionGuideVersionCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProtectionGuideVersionCounterRepository
        extends JpaRepository<ProtectionGuideVersionCounter, ProtectionType> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProtectionGuideVersionCounter c where c.protectionType = :protectionType")
    Optional<ProtectionGuideVersionCounter> findByProtectionTypeForUpdate(ProtectionType protectionType);
}
