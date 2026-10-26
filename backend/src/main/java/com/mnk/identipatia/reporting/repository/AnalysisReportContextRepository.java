package com.mnk.identipatia.reporting.repository;

import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnalysisReportContextRepository extends JpaRepository<AnalysisReportContext, UUID> {
}
