package com.mnk.identipatia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_events")
@Getter
@Setter
@NoArgsConstructor
public class ConsentEvent {

    @Id
    @Column(name = "consent_event_id", nullable = false)
    private UUID consentEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StandardSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "consent_version", nullable = false, length = 128)
    private String consentVersion;

    @Column(name = "consent_document_hash", nullable = false, length = 64)
    private String consentDocumentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConsentDecision decision;

    @Column(length = 32)
    private String source;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
