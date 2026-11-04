package com.mnk.identipatia.analysis.repository;

import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID>, JpaSpecificationExecutor<Analysis> {
    Optional<Analysis> findByAnalysisIdAndSessionId(UUID analysisId, UUID sessionId);
    Optional<Analysis> findByAnalysisIdAndUserIdAndSessionIdIsNull(UUID analysisId, Long userId);
    Optional<Analysis> findByAnalysisIdAndLeaseOwnerAndStatus(
            UUID analysisId, String leaseOwner, AnalysisStatus status);

}
