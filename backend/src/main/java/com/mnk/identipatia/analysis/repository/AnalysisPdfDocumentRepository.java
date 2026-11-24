package com.mnk.identipatia.analysis.repository;

import com.mnk.identipatia.analysis.model.AnalysisPdfDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnalysisPdfDocumentRepository extends JpaRepository<AnalysisPdfDocument, UUID> {
}
