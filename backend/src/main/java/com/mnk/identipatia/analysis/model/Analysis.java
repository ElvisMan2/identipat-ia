package com.mnk.identipatia.analysis.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analyses")
@Getter
@Setter
@NoArgsConstructor
public class Analysis {
    @Id
    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "consent_event_id", nullable = false)
    private UUID consentEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 16)
    private AnalysisInputType inputType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_owner", length = 80)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;
}
