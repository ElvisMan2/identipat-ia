package com.mnk.identipatia.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.service.AnalysisAccessContext;
import com.mnk.identipatia.analysis.service.AnalysisAdmissionService;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "app.analysis.worker-enabled=false",
        "app.analysis.admission.enabled=true"
})
@ActiveProfiles("test")
@Testcontainers
class AnalysisAdmissionServiceIntegrationTest {
    private static final long ADVISORY_LOCK_KEY = 4_942_446_500_001L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_admission_test");

    @Autowired AnalysisAdmissionService admissionService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean Clock clock;

    private Instant now;

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM analysis_admissions");
        jdbc.update("DELETE FROM analysis_inputs");
        jdbc.update("DELETE FROM analyses");
        jdbc.update("DELETE FROM users");
        now = databaseNow();
        when(clock.instant()).thenReturn(now);
    }

    @Test
    void confirmationAtomicallyPersistsAnalysisInputAndAcceptedAdmission() {
        AnalysisAccessContext admin = AnalysisAccessContext.admin(user("ADMIN"));
        var reservation = admissionService.reserve(admin, AnalysisInputType.TEXT);

        var created = admissionService.confirm(reservation, admin, AnalysisInputType.TEXT,
                "Descripción técnica original suficiente.", "Descripción técnica original suficiente.",
                objectMapper.createObjectNode(), null);

        assertThat(jdbc.queryForObject("SELECT state FROM analysis_admissions WHERE admission_id = ?",
                String.class, reservation.admissionId())).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT analysis_id FROM analysis_admissions WHERE admission_id = ?",
                UUID.class, reservation.admissionId())).isEqualTo(created.analysisId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_inputs WHERE analysis_id = ?",
                Integer.class, created.analysisId())).isEqualTo(1);
    }

    @Test
    void twoConcurrentRequestsForSameStandardUserAdmitExactlyOne() throws Exception {
        AnalysisAccessContext standard = standard(user("STANDARD"));

        List<Object> outcomes = race(
                () -> admissionService.reserve(standard, AnalysisInputType.TEXT),
                () -> admissionService.reserve(standard, AnalysisInputType.TEXT));

        assertThat(outcomes.stream().filter(AnalysisAdmissionService.Reservation.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(ApiException.class::isInstance)
                .map(ApiException.class::cast).map(ApiException::getCode))
                .containsExactly("ANALYSIS_ALREADY_ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_admissions WHERE state = 'RESERVED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void twoUsersCompetingForLastGlobalSlotAdmitExactlyOne() throws Exception {
        for (int index = 0; index < 7; index++) {
            insertAccepted(user("ADMIN"), AnalysisInputType.TEXT, now.minusSeconds(index + 1), "RECEIVED");
        }
        AnalysisAccessContext first = AnalysisAccessContext.admin(user("ADMIN"));
        AnalysisAccessContext second = AnalysisAccessContext.admin(user("ADMIN"));

        List<Object> outcomes = race(
                () -> admissionService.reserve(first, AnalysisInputType.TEXT),
                () -> admissionService.reserve(second, AnalysisInputType.TEXT));

        assertThat(outcomes.stream().filter(AnalysisAdmissionService.Reservation.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(ApiException.class::isInstance)
                .map(ApiException.class::cast).map(ApiException::getCode))
                .containsExactly("ANALYSIS_CAPACITY_EXCEEDED");
    }

    @Test
    void hourlyQuotaUsesRollingWindowAndPositiveRetryAfter() {
        User user = user("STANDARD");
        UUID oldest = insertAccepted(user, AnalysisInputType.TEXT, now.minusSeconds(30 * 60), "COMPLETED");
        insertAccepted(user, AnalysisInputType.TEXT, now.minusSeconds(20 * 60), "FAILED");
        insertAccepted(user, AnalysisInputType.TEXT, now.minusSeconds(10 * 60), "COMPLETED");

        assertThatThrownBy(() -> admissionService.reserve(standard(user), AnalysisInputType.TEXT))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("ANALYSIS_QUOTA_EXCEEDED");
                    assertThat(exception.getRetryAfterSeconds()).isPositive();
                });

        Instant outsideHourlyWindow = databaseNow().minus(Duration.ofMinutes(61));
        jdbc.update("""
                UPDATE analysis_admissions
                SET reserved_at = ?, accepted_at = ?, expires_at = ?
                WHERE analysis_id = ?
                """, atUtc(outsideHourlyWindow), atUtc(outsideHourlyWindow),
                atUtc(outsideHourlyWindow.plusSeconds(480)), oldest);
        assertThat(admissionService.reserve(standard(user), AnalysisInputType.TEXT).enabled()).isTrue();
    }

    @Test
    void dailyAndAudioQuotasAreEnforcedOnAcceptedTerminalAnalyses() {
        User dailyUser = user("STANDARD");
        for (int index = 1; index <= 10; index++) {
            insertAccepted(dailyUser, AnalysisInputType.TEXT, now.minusSeconds(index * 2L * 3600), "COMPLETED");
        }
        assertThatThrownBy(() -> admissionService.reserve(standard(dailyUser), AnalysisInputType.TEXT))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ANALYSIS_QUOTA_EXCEEDED"));

        User audioUser = user("STANDARD");
        for (int index = 1; index <= 3; index++) {
            insertAccepted(audioUser, AnalysisInputType.AUDIO, now.minusSeconds(index * 2L * 3600), "FAILED");
        }
        assertThatThrownBy(() -> admissionService.reserve(standard(audioUser), AnalysisInputType.AUDIO))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ANALYSIS_QUOTA_EXCEEDED"));
    }

    @Test
    void terminalAnalysisReleasesActiveSlotButKeepsQuotaConsumption() {
        User user = user("STANDARD");
        UUID analysisId = insertAccepted(user, AnalysisInputType.TEXT, now.minusSeconds(60), "RECEIVED");

        assertThatThrownBy(() -> admissionService.reserve(standard(user), AnalysisInputType.TEXT))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ANALYSIS_ALREADY_ACTIVE"));
        jdbc.update("UPDATE analyses SET status = 'COMPLETED', completed_at = ?, updated_at = ? WHERE analysis_id = ?",
                atUtc(now), atUtc(now), analysisId);

        assertThat(admissionService.reserve(standard(user), AnalysisInputType.TEXT).enabled()).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM analysis_admissions
                WHERE user_id = ? AND state IN ('ACCEPTED', 'RESERVED')
                """, Integer.class, user.getUserId())).isEqualTo(2);
    }

    @Test
    void onlyOneConcurrentAudioReservationIsAdmitted() throws Exception {
        AnalysisAccessContext first = AnalysisAccessContext.admin(user("ADMIN"));
        AnalysisAccessContext second = AnalysisAccessContext.admin(user("ADMIN"));

        List<Object> outcomes = race(
                () -> admissionService.reserve(first, AnalysisInputType.AUDIO),
                () -> admissionService.reserve(second, AnalysisInputType.AUDIO));

        assertThat(outcomes.stream().filter(AnalysisAdmissionService.Reservation.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(ApiException.class::isInstance)
                .map(ApiException.class::cast).map(ApiException::getCode))
                .containsExactly("AUDIO_PREPROCESSING_BUSY");
    }

    @Test
    void expiredReservationIsRecoveredAndCannotBeConfirmedLate() {
        AnalysisAccessContext admin = AnalysisAccessContext.admin(user("ADMIN"));
        var expired = admissionService.reserve(admin, AnalysisInputType.TEXT);
        jdbc.update("""
                UPDATE analysis_admissions
                SET reserved_at = clock_timestamp() - interval '9 minutes',
                    expires_at = clock_timestamp() - interval '1 second'
                WHERE admission_id = ?
                """, expired.admissionId());

        var replacement = admissionService.reserve(AnalysisAccessContext.admin(user("ADMIN")),
                AnalysisInputType.TEXT);
        assertThat(replacement.enabled()).isTrue();
        assertThatThrownBy(() -> admissionService.confirm(expired, admin, AnalysisInputType.TEXT,
                "Descripción suficientemente extensa.", "Descripción suficientemente extensa.",
                objectMapper.createObjectNode(), null)).isInstanceOf(ApiException.class);
        admissionService.release(expired);
        admissionService.release(expired);

        assertThat(jdbc.queryForObject("SELECT state FROM analysis_admissions WHERE admission_id = ?",
                String.class, expired.admissionId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isZero();
    }

    @Test
    void confirmUsesDatabaseTimeAfterWaitingForAdvisoryLock() throws Exception {
        AnalysisAccessContext admin = AnalysisAccessContext.admin(user("ADMIN"));
        var reservation = admissionService.reserve(admin, AnalysisInputType.TEXT);

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, ADVISORY_LOCK_KEY);
                lock.execute();
            }

            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<Object> confirmation = pool.submit(() -> captureApiException(() ->
                        admissionService.confirm(reservation, admin, AnalysisInputType.TEXT,
                                "Descripción suficientemente extensa.",
                                "Descripción suficientemente extensa.",
                                objectMapper.createObjectNode(), null)));

                awaitAdvisoryLockWaiter();
                try (PreparedStatement expire = holder.prepareStatement("""
                        UPDATE analysis_admissions
                        SET reserved_at = clock_timestamp() - interval '9 minutes',
                            expires_at = clock_timestamp() - interval '1 second'
                        WHERE admission_id = ?
                        """)) {
                    expire.setObject(1, reservation.admissionId());
                    assertThat(expire.executeUpdate()).isEqualTo(1);
                }
                holder.commit();

                assertThat(confirmation.get(10, TimeUnit.SECONDS))
                        .isInstanceOfSatisfying(ApiException.class,
                                exception -> assertThat(exception.getCode())
                                        .isEqualTo("ANALYSIS_CAPACITY_EXCEEDED"));
            } finally {
                if (!holder.getAutoCommit()) {
                    holder.rollback();
                }
            }
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isZero();
        admissionService.release(reservation);
        assertThat(admissionState(reservation)).isEqualTo("RELEASED");
    }

    @Test
    void confirmationFailureAfterFlushRollsBackAnalysisAndInput() {
        AnalysisAccessContext admin = AnalysisAccessContext.admin(user("ADMIN"));
        var reservation = admissionService.reserve(admin, AnalysisInputType.TEXT);
        jdbc.execute("""
                CREATE FUNCTION reject_test_admission_acceptance() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.state = 'ACCEPTED' THEN
                        RAISE EXCEPTION 'synthetic acceptance failure';
                    END IF;
                    RETURN NEW;
                END
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_test_admission_acceptance_trigger
                BEFORE UPDATE ON analysis_admissions
                FOR EACH ROW EXECUTE FUNCTION reject_test_admission_acceptance()
                """);
        try {
            assertThatThrownBy(() -> admissionService.confirm(
                    reservation, admin, AnalysisInputType.TEXT,
                    "Descripción suficientemente extensa.",
                    "Descripción suficientemente extensa.",
                    objectMapper.createObjectNode(), null))
                    .isInstanceOf(RuntimeException.class);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_inputs", Integer.class)).isZero();
            assertThat(admissionState(reservation)).isEqualTo("RESERVED");
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_admission_acceptance_trigger ON analysis_admissions");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_test_admission_acceptance()");
            admissionService.release(reservation);
        }
    }

    @Test
    void concurrentConfirmAndReleaseProduceOneCompatibleFinalState() throws Exception {
        AnalysisAccessContext admin = AnalysisAccessContext.admin(user("ADMIN"));
        var reservation = admissionService.reserve(admin, AnalysisInputType.TEXT);

        List<Object> outcomes = race(
                () -> admissionService.confirm(reservation, admin, AnalysisInputType.TEXT,
                        "Descripción suficientemente extensa.",
                        "Descripción suficientemente extensa.",
                        objectMapper.createObjectNode(), null),
                () -> {
                    admissionService.release(reservation);
                    return "released";
                });

        String state = admissionState(reservation);
        if ("ACCEPTED".equals(state)) {
            assertThat(outcomes).anyMatch(com.mnk.identipatia.analysis.dto.AnalysisCreatedResponse.class::isInstance);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_inputs", Integer.class)).isEqualTo(1);
            admissionService.release(reservation);
            assertThat(admissionState(reservation)).isEqualTo("ACCEPTED");
        } else {
            assertThat(state).isEqualTo("RELEASED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_inputs", Integer.class)).isZero();
            admissionService.release(reservation);
            assertThat(admissionState(reservation)).isEqualTo("RELEASED");
        }
    }

    @Test
    void retryAfterUsesLatestOpportunityAcrossHourlyDailyAndAudioQuotas() {
        User user = user("STANDARD");
        for (int hours = 23; hours >= 17; hours--) {
            insertAccepted(user, AnalysisInputType.TEXT, now.minus(Duration.ofHours(hours)), "COMPLETED");
        }
        insertAccepted(user, AnalysisInputType.AUDIO, now.minus(Duration.ofMinutes(50)), "FAILED");
        insertAccepted(user, AnalysisInputType.AUDIO, now.minus(Duration.ofMinutes(40)), "FAILED");
        insertAccepted(user, AnalysisInputType.AUDIO, now.minus(Duration.ofMinutes(30)), "FAILED");

        assertThatThrownBy(() -> admissionService.reserve(standard(user), AnalysisInputType.AUDIO))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("ANALYSIS_QUOTA_EXCEEDED");
                    assertThat(exception.getRetryAfterSeconds()).isBetween(83_300L, 83_500L);
                });
    }

    private List<Object> race(ThrowingSupplier first, ThrowingSupplier second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Set<Integer> backendPids = ConcurrentHashMap.newKeySet();
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Object> one = pool.submit(() -> outcome(ready, start, backendPids, first));
            Future<Object> two = pool.submit(() -> outcome(ready, start, backendPids, second));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> outcomes = List.of(one.get(), two.get());
            assertThat(backendPids).hasSize(2);
            return outcomes;
        }
    }

    private Object outcome(CountDownLatch ready, CountDownLatch start, Set<Integer> backendPids,
            ThrowingSupplier supplier) {
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                backendPids.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                ready.countDown();
                await(start);
                return supplier.get();
            });
        } catch (ApiException exception) {
            return exception;
        }
    }

    private void awaitAdvisoryLockWaiter() {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM pg_locks
                        WHERE locktype = 'advisory' AND NOT granted
                        """, Integer.class)).isPositive());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent admission test was interrupted", exception);
        }
    }

    private static Object captureApiException(ThrowingSupplier supplier) {
        try {
            return supplier.get();
        } catch (ApiException exception) {
            return exception;
        }
    }

    private String admissionState(AnalysisAdmissionService.Reservation reservation) {
        return jdbc.queryForObject("SELECT state FROM analysis_admissions WHERE admission_id = ?",
                String.class, reservation.admissionId());
    }

    private Instant databaseNow() {
        return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
    }

    private User user(String type) {
        User user = new User();
        user.setDoi("ADMISSION-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType(type);
        user.setStatus("A");
        return userRepository.saveAndFlush(user);
    }

    private static AnalysisAccessContext standard(User user) {
        StandardSession session = new StandardSession();
        session.setSessionId(UUID.randomUUID());
        session.setUser(user);
        return AnalysisAccessContext.standard(user, session);
    }

    private UUID insertAccepted(User user, AnalysisInputType type, Instant reservedAt, String status) {
        UUID analysisId = UUID.randomUUID();
        boolean completed = "COMPLETED".equals(status);
        boolean failed = "FAILED".equals(status);
        jdbc.update("""
                INSERT INTO analyses
                    (analysis_id, user_id, input_type, status, created_at, completed_at, failed_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, analysisId, user.getUserId(), type.name(), status, atUtc(reservedAt),
                completed ? atUtc(reservedAt) : null, failed ? atUtc(reservedAt) : null, atUtc(reservedAt));
        jdbc.update("""
                INSERT INTO analysis_admissions
                    (admission_id, user_id, analysis_id, input_type, actor_mode, state,
                     reserved_at, expires_at, accepted_at)
                VALUES (?, ?, ?, ?, 'STANDARD_SESSION', 'ACCEPTED', ?, ?, ?)
                """, UUID.randomUUID(), user.getUserId(), analysisId, type.name(), atUtc(reservedAt),
                atUtc(reservedAt.plusSeconds(480)), atUtc(reservedAt));
        return analysisId;
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get();
    }
}
