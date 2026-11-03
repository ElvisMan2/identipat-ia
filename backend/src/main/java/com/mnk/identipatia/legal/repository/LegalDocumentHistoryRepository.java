package com.mnk.identipatia.legal.repository;

import com.mnk.identipatia.legal.model.LegalDocumentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalDocumentHistoryRepository extends JpaRepository<LegalDocumentHistory, String> {
}

