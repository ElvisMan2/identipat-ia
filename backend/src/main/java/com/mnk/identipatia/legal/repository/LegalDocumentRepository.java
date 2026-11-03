package com.mnk.identipatia.legal.repository;

import com.mnk.identipatia.legal.model.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {
    @Query("select document from LegalDocument document")
    Optional<LegalDocument> findCurrent();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from LegalDocument document")
    Optional<LegalDocument> findCurrentForUpdate();
}

