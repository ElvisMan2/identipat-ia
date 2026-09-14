package com.mnk.identipatia.analysis.repository;

import com.mnk.identipatia.analysis.model.AnalysisInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnalysisInputRepository extends JpaRepository<AnalysisInput, UUID> {
}
