package com.mnk.identipatia.analysis.repository;

import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoredAnalysisResultRepository extends JpaRepository<StoredAnalysisResult, UUID> {
}
