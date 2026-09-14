package com.mnk.identipatia.repository;

import com.mnk.identipatia.model.ConsentDecision;
import com.mnk.identipatia.model.ConsentEvent;
import com.mnk.identipatia.model.StandardSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsentEventRepository extends JpaRepository<ConsentEvent, UUID> {
    Optional<ConsentEvent> findBySessionAndConsentVersion(StandardSession session, String consentVersion);
    boolean existsBySessionAndConsentVersionAndDecision(
            StandardSession session, String consentVersion, ConsentDecision decision);
    Optional<ConsentEvent> findBySessionSessionIdAndUserUserIdAndConsentVersionAndConsentDocumentHashAndDecision(
            UUID sessionId, Long userId, String consentVersion, String consentDocumentHash,
            ConsentDecision decision);
}
