package com.mnk.identipatia.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.StandardSessionStatus;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.StandardSessionRepository;
import com.mnk.identipatia.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.analysis.worker-enabled=false",
        "app.analysis.admission.enabled=true"
})
@ActiveProfiles("test")
@Testcontainers
class AnalysisIdempotencyServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_idempotency_test");

    @Autowired AnalysisIdempotencyService idempotencyService;
    @Autowired AnalysisAdmissionService admissionService;
    @Autowired UserRepository userRepository;
    @Autowired StandardSessionRepository sessionRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM analysis_idempotency_keys");
        jdbc.update("DELETE FROM analysis_admissions");
        jdbc.update("DELETE FROM analysis_inputs");
        jdbc.update("DELETE FROM analyses");
        jdbc.update("DELETE FROM consent_events");
        sessionRepository.deleteAll();
        jdbc.update("DELETE FROM users");
    }

    @Test
    void acceptedRequestReplaysOriginalResponseWithoutAnotherAdmission() {
        AnalysisAccessContext access = AnalysisAccessContext.admin(user());
        String key = UUID.randomUUID().toString();
        String description = "Descripción técnica original suficientemente extensa.";
        String digest = idempotencyService.fingerprintText(description);
        var claim = idempotencyService.claimOrReplay(access, key, AnalysisInputType.TEXT, digest);
        var reservation = admissionService.reserve(access, AnalysisInputType.TEXT);

        var created = admissionService.confirm(reservation, claim, access, AnalysisInputType.TEXT,
                description, description, objectMapper.createObjectNode(), null);
        var replay = idempotencyService.claimOrReplay(access, key, AnalysisInputType.TEXT, digest);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.replay()).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_inputs", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_admissions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM analysis_idempotency_keys WHERE idempotency_id = ?",
                String.class, claim.idempotencyId())).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT key_digest FROM analysis_idempotency_keys WHERE idempotency_id = ?",
                String.class, claim.idempotencyId())).doesNotContain(key);
    }

    @Test
    void sameKeyWithDifferentPayloadConflictsAndDifferentUsersRemainIsolated() {
        String key = UUID.randomUUID().toString();
        AnalysisAccessContext first = AnalysisAccessContext.admin(user());
        AnalysisAccessContext second = AnalysisAccessContext.admin(user());
        String firstDigest = idempotencyService.fingerprintText("Primera descripción suficientemente extensa.");
        String secondDigest = idempotencyService.fingerprintText("Segunda descripción suficientemente extensa.");

        idempotencyService.claimOrReplay(first, key, AnalysisInputType.TEXT, firstDigest);
        assertThatThrownBy(() -> idempotencyService.claimOrReplay(
                first, key, AnalysisInputType.TEXT, secondDigest))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
        assertThat(idempotencyService.claimOrReplay(
                second, key, AnalysisInputType.TEXT, firstDigest).claimed()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_idempotency_keys", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void acceptedReplayIsScopedToTheSameStandardSession() {
        User standard = standardUser();
        AnalysisAccessContext firstSession = AnalysisAccessContext.standard(standard, session(standard));
        AnalysisAccessContext secondSession = AnalysisAccessContext.standard(standard, session(standard));
        String key = UUID.randomUUID().toString();
        String description = "Descripción STANDARD suficientemente extensa para validar sesiones.";
        String digest = idempotencyService.fingerprintText(description);
        var claim = idempotencyService.claimOrReplay(
                firstSession, key, AnalysisInputType.TEXT, digest);
        UUID analysisId = acceptedLegacyStandardAnalysis(firstSession);
        idempotencyService.accept(claim, analysisId);

        var replay = idempotencyService.claimOrReplay(
                firstSession, key, AnalysisInputType.TEXT, digest);
        var isolatedClaim = idempotencyService.claimOrReplay(
                secondSession, key, AnalysisInputType.TEXT, digest);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.replay().analysisId()).isEqualTo(analysisId);
        assertThat(isolatedClaim.claimed()).isTrue();
        assertThat(isolatedClaim.idempotencyId()).isNotEqualTo(claim.idempotencyId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_idempotency_keys", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void concurrentClaimsAcrossConnectionsAllowExactlyOneOwner() throws Exception {
        AnalysisAccessContext access = AnalysisAccessContext.admin(user());
        String key = UUID.randomUUID().toString();
        String digest = idempotencyService.fingerprintText("Descripción concurrente suficientemente extensa.");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Object> one = pool.submit(() -> claimOutcome(ready, start, access, key, digest));
            Future<Object> two = pool.submit(() -> claimOutcome(ready, start, access, key, digest));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> outcomes = List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(AnalysisIdempotencyService.Claim.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(ApiException.class::isInstance)
                    .map(ApiException.class::cast).map(ApiException::getCode))
                    .containsExactly("IDEMPOTENCY_REQUEST_IN_PROGRESS");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_idempotency_keys", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void expiredClaimCanBeRecoveredAndFencesLateOwner() {
        AnalysisAccessContext access = AnalysisAccessContext.admin(user());
        String key = UUID.randomUUID().toString();
        String description = "Descripción recuperable suficientemente extensa.";
        String digest = idempotencyService.fingerprintText(description);
        var oldClaim = idempotencyService.claimOrReplay(access, key, AnalysisInputType.TEXT, digest);
        jdbc.update("""
                UPDATE analysis_idempotency_keys
                SET claim_expires_at = clock_timestamp() - interval '1 second'
                WHERE idempotency_id = ?
                """, oldClaim.idempotencyId());
        var recovered = idempotencyService.claimOrReplay(access, key, AnalysisInputType.TEXT, digest);
        var reservation = admissionService.reserve(access, AnalysisInputType.TEXT);

        assertThat(recovered.idempotencyId()).isEqualTo(oldClaim.idempotencyId());
        assertThat(recovered.claimToken()).isNotEqualTo(oldClaim.claimToken());
        assertThatThrownBy(() -> admissionService.confirm(
                reservation, oldClaim, access, AnalysisInputType.TEXT,
                description, description, objectMapper.createObjectNode(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("IDEMPOTENCY_REQUEST_IN_PROGRESS"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM analysis_admissions WHERE admission_id = ?",
                String.class, reservation.admissionId())).isEqualTo("RESERVED");

        var created = admissionService.confirm(
                reservation, recovered, access, AnalysisInputType.TEXT,
                description, description, objectMapper.createObjectNode(), null);
        assertThat(created.analysisId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isEqualTo(1);
    }

    @Test
    void recoveredClaimRefreshesRetentionBeyondItsNewLease() {
        AnalysisAccessContext access = AnalysisAccessContext.admin(user());
        String key = UUID.randomUUID().toString();
        String digest = idempotencyService.fingerprintText(
                "Descripción recuperable con renovación temporal segura.");
        var first = idempotencyService.claimOrReplay(access, key, AnalysisInputType.TEXT, digest);
        jdbc.update("""
                UPDATE analysis_idempotency_keys
                SET claim_expires_at = clock_timestamp() - interval '1 second'
                WHERE idempotency_id = ?
                """, first.idempotencyId());

        var recovered = idempotencyService.claimOrReplay(access, key, AnalysisInputType.TEXT, digest);

        assertThat(recovered.claimToken()).isNotEqualTo(first.claimToken());
        assertThat(jdbc.queryForObject("""
                SELECT claim_expires_at < expires_at
                FROM analysis_idempotency_keys
                WHERE idempotency_id = ?
                """, Boolean.class, first.idempotencyId())).isTrue();
    }

    @Test
    void purgeNeverDeletesAnInProgressClaimWhoseLeaseIsStillValid() {
        AnalysisAccessContext access = AnalysisAccessContext.admin(user());
        String digest = idempotencyService.fingerprintText(
                "Descripción con claim vigente que la purga debe conservar.");
        var active = idempotencyService.claimOrReplay(
                access, UUID.randomUUID().toString(), AnalysisInputType.TEXT, digest);
        jdbc.execute("ALTER TABLE analysis_idempotency_keys "
                + "DROP CONSTRAINT ck_analysis_idempotency_claim_within_retention");
        try {
            jdbc.update("""
                    UPDATE analysis_idempotency_keys
                    SET created_at = clock_timestamp() - interval '2 hours',
                        expires_at = clock_timestamp() - interval '1 hour',
                        claim_expires_at = clock_timestamp() + interval '1 hour'
                    WHERE idempotency_id = ?
                    """, active.idempotencyId());

            idempotencyService.claimOrReplay(
                    access, UUID.randomUUID().toString(), AnalysisInputType.TEXT, digest);

            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM analysis_idempotency_keys WHERE idempotency_id = ?
                    """, Integer.class, active.idempotencyId())).isEqualTo(1);
        } finally {
            jdbc.update("""
                    UPDATE analysis_idempotency_keys
                    SET expires_at = claim_expires_at + interval '1 hour'
                    WHERE state = 'IN_PROGRESS' AND claim_expires_at >= expires_at
                    """);
            jdbc.execute("ALTER TABLE analysis_idempotency_keys "
                    + "ADD CONSTRAINT ck_analysis_idempotency_claim_within_retention "
                    + "CHECK (state <> 'IN_PROGRESS' OR claim_expires_at < expires_at)");
        }
    }

    @Test
    void databaseRejectsAClaimLeaseOutsideItsRetentionWindow() {
        AnalysisAccessContext access = AnalysisAccessContext.admin(user());
        var claim = idempotencyService.claimOrReplay(
                access, UUID.randomUUID().toString(), AnalysisInputType.TEXT,
                idempotencyService.fingerprintText(
                        "Descripción para validar el constraint temporal de PostgreSQL."));

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE analysis_idempotency_keys
                SET expires_at = claim_expires_at
                WHERE idempotency_id = ?
                """, claim.idempotencyId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void releasedClaimCanBeReclaimedButNeverByAChangedPayload() {
        AnalysisAccessContext access = AnalysisAccessContext.admin(user());
        String key = UUID.randomUUID().toString();
        String digest = idempotencyService.fingerprintText("Descripción liberada suficientemente extensa.");
        var first = idempotencyService.claimOrReplay(access, key, AnalysisInputType.TEXT, digest);

        idempotencyService.release(first);
        var second = idempotencyService.claimOrReplay(access, key, AnalysisInputType.TEXT, digest);

        assertThat(second.idempotencyId()).isEqualTo(first.idempotencyId());
        assertThat(second.claimToken()).isNotEqualTo(first.claimToken());
        assertThatThrownBy(() -> idempotencyService.claimOrReplay(
                access, key, AnalysisInputType.PDF,
                idempotencyService.fingerprintFile(
                        AnalysisInputType.PDF, "application/pdf", "%PDF-test".getBytes())))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void idempotencyAcceptanceFailureRollsBackAnalysisInputAndAdmissionTogether() {
        AnalysisAccessContext access = AnalysisAccessContext.admin(user());
        String description = "Descripción para comprobar el rollback atómico completo.";
        var claim = idempotencyService.claimOrReplay(
                access, UUID.randomUUID().toString(), AnalysisInputType.TEXT,
                idempotencyService.fingerprintText(description));
        var reservation = admissionService.reserve(access, AnalysisInputType.TEXT);
        jdbc.execute("""
                CREATE FUNCTION reject_test_idempotency_acceptance() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.state = 'ACCEPTED' THEN
                        RAISE EXCEPTION 'synthetic idempotency acceptance failure';
                    END IF;
                    RETURN NEW;
                END
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_test_idempotency_acceptance_trigger
                BEFORE UPDATE ON analysis_idempotency_keys
                FOR EACH ROW EXECUTE FUNCTION reject_test_idempotency_acceptance()
                """);
        try {
            assertThatThrownBy(() -> admissionService.confirm(
                    reservation, claim, access, AnalysisInputType.TEXT,
                    description, description, objectMapper.createObjectNode(), null))
                    .isInstanceOf(RuntimeException.class);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_inputs", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT state FROM analysis_admissions WHERE admission_id = ?",
                    String.class, reservation.admissionId())).isEqualTo("RESERVED");
            assertThat(jdbc.queryForObject(
                    "SELECT state FROM analysis_idempotency_keys WHERE idempotency_id = ?",
                    String.class, claim.idempotencyId())).isEqualTo("IN_PROGRESS");
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_idempotency_acceptance_trigger "
                    + "ON analysis_idempotency_keys");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_test_idempotency_acceptance()");
            admissionService.release(reservation);
            idempotencyService.release(claim);
        }
    }

    @Test
    void invalidOrMissingRequiredKeysFailBeforePersistence() {
        AnalysisAccessContext access = AnalysisAccessContext.admin(user());
        String digest = idempotencyService.fingerprintText("Descripción suficientemente extensa.");

        assertThatThrownBy(() -> idempotencyService.claimOrReplay(
                access, "not-a-uuid", AnalysisInputType.TEXT, digest))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_IDEMPOTENCY_KEY"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_idempotency_keys", Integer.class))
                .isZero();
    }

    private Object claimOutcome(
            CountDownLatch ready,
            CountDownLatch start,
            AnalysisAccessContext access,
            String key,
            String digest) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent claim start timed out");
            }
            return idempotencyService.claimOrReplay(access, key, AnalysisInputType.TEXT, digest);
        } catch (ApiException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private User user() {
        User user = new User();
        user.setDoi("IDEMP-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType("ADMIN");
        user.setStatus("A");
        return userRepository.saveAndFlush(user);
    }

    private User standardUser() {
        User user = new User();
        user.setDoi("STANDARD-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType("STANDARD");
        user.setStatus("A");
        return userRepository.saveAndFlush(user);
    }

    private StandardSession session(User user) {
        Instant now = Instant.now();
        StandardSession session = new StandardSession();
        session.setSessionId(UUID.randomUUID());
        session.setUser(user);
        session.setTokenHash(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        session.setStatus(StandardSessionStatus.ACTIVE);
        session.setCreatedAt(now);
        session.setLastActivityAt(now);
        session.setExpiresAt(now.plusSeconds(1_800));
        session.setAbsoluteExpiresAt(now.plusSeconds(28_800));
        return sessionRepository.saveAndFlush(session);
    }

    private UUID acceptedLegacyStandardAnalysis(AnalysisAccessContext access) {
        UUID consentEventId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO consent_events
                    (consent_event_id, session_id, user_id, consent_version,
                     consent_document_hash, decision, source, decided_at)
                VALUES (?, ?, ?, ?, ?, 'ACCEPTED', 'STANDARD_WEB', clock_timestamp())
                """, consentEventId, access.session().getSessionId(), access.user().getUserId(),
                "idempotency-test-" + consentEventId, "a".repeat(64));
        jdbc.update("""
                INSERT INTO analyses
                    (analysis_id, user_id, session_id, consent_event_id,
                     input_type, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'TEXT', 'RECEIVED', clock_timestamp(), clock_timestamp())
                """, analysisId, access.user().getUserId(), access.session().getSessionId(), consentEventId);
        return analysisId;
    }
}
