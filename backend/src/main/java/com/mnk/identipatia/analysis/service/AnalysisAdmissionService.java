package com.mnk.identipatia.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mnk.identipatia.analysis.config.AnalysisAdmissionProperties;
import com.mnk.identipatia.analysis.dto.AnalysisCreatedResponse;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.legal.model.UserTermsAcceptance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AnalysisAdmissionService {
    // Stable, application-specific key. The transaction-scoped PostgreSQL lock
    // serializes admission decisions across every backend JVM.
    static final long ADVISORY_LOCK_KEY = 4_942_446_500_001L;

    private final JdbcTemplate jdbc;
    private final AnalysisAdmissionProperties properties;
    private final AnalysisCreationPersistenceService creationService;
    private final AnalysisIdempotencyService idempotencyService;

    public AnalysisAdmissionService(
            JdbcTemplate jdbc,
            AnalysisAdmissionProperties properties,
            AnalysisCreationPersistenceService creationService,
            AnalysisIdempotencyService idempotencyService) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.creationService = creationService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public Reservation reserve(AnalysisAccessContext access, AnalysisInputType inputType) {
        if (!properties.enabled()) {
            return Reservation.disabled();
        }
        lock();
        Instant now = databaseNow();
        expire(now);

        long userId = access.user().getUserId();
        boolean standard = access.mode() == AnalysisAccessContext.Mode.STANDARD_SESSION;
        if (standard) {
            enforceQuota(userId, inputType, now);
            long active = count("""
                    SELECT COUNT(*)
                    FROM analysis_admissions admission
                    LEFT JOIN analyses analysis ON analysis.analysis_id = admission.analysis_id
                    WHERE admission.user_id = ?
                      AND admission.actor_mode = 'STANDARD_SESSION'
                      AND ((admission.state = 'RESERVED' AND admission.expires_at > ?)
                        OR (admission.state = 'ACCEPTED'
                            AND analysis.status IN ('RECEIVED', 'PREPROCESSING', 'ANALYZING')))
                    """, userId, atUtc(now));
            if (active >= properties.standardMaxActive()) {
                reject(HttpStatus.TOO_MANY_REQUESTS, "ANALYSIS_ALREADY_ACTIVE",
                        "An analysis is already active for this user", properties.retryAfter());
            }
        }

        long globalActive = count("""
                SELECT COUNT(*)
                FROM analysis_admissions admission
                LEFT JOIN analyses analysis ON analysis.analysis_id = admission.analysis_id
                WHERE (admission.state = 'RESERVED' AND admission.expires_at > ?)
                   OR (admission.state = 'ACCEPTED'
                       AND analysis.status IN ('RECEIVED', 'PREPROCESSING', 'ANALYZING'))
                """, atUtc(now));
        if (globalActive >= properties.globalMaxActive()) {
            reject(HttpStatus.SERVICE_UNAVAILABLE, "ANALYSIS_CAPACITY_EXCEEDED",
                    "Analysis capacity is temporarily unavailable", properties.retryAfter());
        }

        if (inputType == AnalysisInputType.AUDIO) {
            long audioReservations = count("""
                    SELECT COUNT(*) FROM analysis_admissions
                    WHERE input_type = 'AUDIO' AND state = 'RESERVED' AND expires_at > ?
                    """, atUtc(now));
            if (audioReservations >= properties.audioMaxPreprocessing()) {
                reject(HttpStatus.SERVICE_UNAVAILABLE, "AUDIO_PREPROCESSING_BUSY",
                        "Audio preprocessing is temporarily busy", properties.retryAfter());
            }
        }

        UUID admissionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO analysis_admissions
                    (admission_id, user_id, input_type, actor_mode, state, reserved_at, expires_at)
                VALUES (?, ?, ?, ?, 'RESERVED', ?, ?)
                """, admissionId, userId, inputType.name(), access.mode().name(), atUtc(now),
                atUtc(now.plus(properties.reservationTtl())));
        return new Reservation(admissionId, true);
    }

    @Transactional
    public AnalysisCreatedResponse confirm(
            Reservation reservation,
            AnalysisAccessContext access,
            AnalysisInputType inputType,
            String originalText,
            String processedText,
            JsonNode sourceMetadata,
            UserTermsAcceptance acceptance) {
        return confirm(reservation, AnalysisIdempotencyService.Claim.disabled(), access,
                inputType, originalText, processedText, sourceMetadata, acceptance);
    }

    @Transactional
    public AnalysisCreatedResponse confirm(
            Reservation reservation,
            AnalysisIdempotencyService.Claim idempotencyClaim,
            AnalysisAccessContext access,
            AnalysisInputType inputType,
            String originalText,
            String processedText,
            JsonNode sourceMetadata,
            UserTermsAcceptance acceptance) {
        if (!reservation.enabled()) {
            AnalysisCreatedResponse created = creationService.create(
                    access, inputType, originalText, processedText, sourceMetadata, acceptance);
            idempotencyService.accept(idempotencyClaim, created.analysisId());
            return created;
        }
        lock();
        Instant now = databaseNow();
        expire(now);
        Integer available = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analysis_admissions
                WHERE admission_id = ? AND state = 'RESERVED' AND expires_at > ?
                """, Integer.class, reservation.admissionId(), atUtc(now));
        if (available == null || available != 1) {
            reject(HttpStatus.SERVICE_UNAVAILABLE, "ANALYSIS_CAPACITY_EXCEEDED",
                    "The analysis reservation expired before it could be accepted", properties.retryAfter());
        }

        AnalysisCreatedResponse created = creationService.create(
                access, inputType, originalText, processedText, sourceMetadata, acceptance);
        int changed = jdbc.update("""
                UPDATE analysis_admissions
                SET state = 'ACCEPTED', analysis_id = ?, accepted_at = ?
                WHERE admission_id = ? AND state = 'RESERVED' AND expires_at > ?
                """, created.analysisId(), atUtc(now), reservation.admissionId(), atUtc(now));
        if (changed != 1) {
            throw new IllegalStateException("Analysis reservation changed during confirmation");
        }
        idempotencyService.accept(idempotencyClaim, created.analysisId());
        return created;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Reservation reservation) {
        if (!reservation.enabled()) {
            return;
        }
        lock();
        Instant now = databaseNow();
        jdbc.update("""
                UPDATE analysis_admissions
                SET state = 'RELEASED', released_at = ?
                WHERE admission_id = ? AND state = 'RESERVED'
                """, atUtc(now), reservation.admissionId());
    }

    private void enforceQuota(long userId, AnalysisInputType inputType, Instant now) {
        List<Duration> exhaustedWindows = new ArrayList<>(3);
        addIfExhausted(exhaustedWindows,
                retryAfterForWindow(userId, null, now, Duration.ofHours(1), properties.standardMaxPerHour()));
        addIfExhausted(exhaustedWindows,
                retryAfterForWindow(userId, null, now, Duration.ofHours(24), properties.standardMaxPer24Hours()));
        if (inputType == AnalysisInputType.AUDIO) {
            addIfExhausted(exhaustedWindows,
                    retryAfterForWindow(userId, AnalysisInputType.AUDIO, now, Duration.ofHours(24),
                            properties.standardAudioMaxPer24Hours()));
        }
        if (!exhaustedWindows.isEmpty()) {
            Duration retry = exhaustedWindows.stream().max(Duration::compareTo).orElse(properties.retryAfter());
            reject(HttpStatus.TOO_MANY_REQUESTS, "ANALYSIS_QUOTA_EXCEEDED",
                    "The analysis quota has been exceeded", retry);
        }
    }

    private Duration retryAfterForWindow(
            long userId, AnalysisInputType inputType, Instant now, Duration window, int limit) {
        Instant threshold = now.minus(window);
        String typeClause = inputType == null ? "" : " AND input_type = 'AUDIO'";
        String base = """
                FROM analysis_admissions
                WHERE user_id = ? AND actor_mode = 'STANDARD_SESSION'
                  AND state IN ('RESERVED', 'ACCEPTED')
                  AND reserved_at > ?
                  AND (state <> 'RESERVED' OR expires_at > ?)
                """ + typeClause;
        List<OffsetDateTime> used = jdbc.queryForList(
                "SELECT reserved_at " + base + " ORDER BY reserved_at",
                OffsetDateTime.class, userId, atUtc(threshold), atUtc(now));
        if (used.size() < limit) {
            return null;
        }
        Instant admissionOpportunity = used.get(used.size() - limit).toInstant().plus(window);
        return Duration.between(now, admissionOpportunity);
    }

    private static void addIfExhausted(List<Duration> exhaustedWindows, Duration retryAfter) {
        if (retryAfter != null) {
            exhaustedWindows.add(retryAfter);
        }
    }

    private void lock() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, ADVISORY_LOCK_KEY);
    }

    private Instant databaseNow() {
        OffsetDateTime value = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        if (value == null) {
            throw new IllegalStateException("PostgreSQL did not return the admission timestamp");
        }
        return value.toInstant();
    }

    private void expire(Instant now) {
        jdbc.update("""
                UPDATE analysis_admissions
                SET state = 'RELEASED', released_at = ?
                WHERE state = 'RESERVED' AND expires_at <= ?
                """, atUtc(now), atUtc(now));
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void reject(HttpStatus status, String code, String message, Duration retryAfter) {
        long seconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
        throw new ApiException(status, code, message, seconds);
    }

    public record Reservation(UUID admissionId, boolean enabled) {
        static Reservation disabled() {
            return new Reservation(null, false);
        }
    }
}
