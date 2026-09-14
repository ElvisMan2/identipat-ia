package com.mnk.identipatia.analysis.repository;

import com.mnk.identipatia.analysis.model.AiInvocation;
import com.mnk.identipatia.analysis.model.AiInvocationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiInvocationRepository extends JpaRepository<AiInvocation, UUID> {
    long countByAnalysisId(UUID analysisId);
    Optional<AiInvocation> findFirstByAnalysisIdAndStatusOrderByAttemptNumberDesc(
            UUID analysisId, AiInvocationStatus status);
    List<AiInvocation> findAllByAnalysisIdOrderByAttemptNumber(UUID analysisId);
}
