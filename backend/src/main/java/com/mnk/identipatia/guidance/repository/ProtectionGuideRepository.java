package com.mnk.identipatia.guidance.repository;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.guidance.model.ProtectionGuide;
import com.mnk.identipatia.guidance.model.ProtectionGuideStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProtectionGuideRepository extends JpaRepository<ProtectionGuide, UUID> {
    List<ProtectionGuide> findAllByOrderByProtectionTypeAscVersionDesc();

    Optional<ProtectionGuide> findByProtectionTypeAndStatus(ProtectionType protectionType,
            ProtectionGuideStatus status);

    Optional<ProtectionGuide> findByProtectionTypeAndVersion(ProtectionType protectionType, Long version);

    List<ProtectionGuide> findAllByProtectionTypeInAndStatus(List<ProtectionType> protectionTypes,
            ProtectionGuideStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ProtectionGuide g where g.guideId = :guideId")
    Optional<ProtectionGuide> findByIdForUpdate(UUID guideId);
}
