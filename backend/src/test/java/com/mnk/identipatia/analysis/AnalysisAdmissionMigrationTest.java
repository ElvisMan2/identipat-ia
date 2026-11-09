package com.mnk.identipatia.analysis;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisAdmissionMigrationTest {

    @Test
    void v10BackfillsLegacyAnalysesWithCompleteAndConstrainedMappings() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("identipat_admission_migration_test")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("9").load().migrate();

            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                OffsetDateTime migrationNow = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
                long adminId = insertUser(connection, "ADMIN");
                long standardId = insertUser(connection, "STANDARD");
                StandardContext standard = insertStandardContext(connection, standardId, migrationNow);

                UUID recentTerminal = insertAnalysis(connection, adminId, null, null,
                        "PDF", migrationNow.minusHours(2), "COMPLETED");
                UUID historicalActive = insertAnalysis(connection, standardId, standard.sessionId(),
                        standard.consentId(), "AUDIO", migrationNow.minusDays(3), "RECEIVED");
                UUID historicalTerminal = insertAnalysis(connection, standardId, standard.sessionId(),
                        standard.consentId(), "TEXT", migrationNow.minusDays(3), "COMPLETED");

                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .load().migrate();

                Map<UUID, AdmissionRow> admissions = readAdmissions(connection);
                assertThat(admissions).containsOnlyKeys(recentTerminal, historicalActive);
                assertThat(admissions).doesNotContainKey(historicalTerminal);

                assertBackfill(admissions.get(recentTerminal), recentTerminal, adminId,
                        "PDF", "ADMIN_JWT", migrationNow.minusHours(2));
                assertBackfill(admissions.get(historicalActive), historicalActive, standardId,
                        "AUDIO", "STANDARD_SESSION", migrationNow.minusDays(3));

                assertThatThrownBy(() -> insertDuplicateAnalysisReference(
                        connection, recentTerminal, adminId, migrationNow))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> insertInvalidAcceptedAdmission(
                        connection, adminId, migrationNow))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    private static long insertUser(java.sql.Connection connection, String type) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO users (doi, status, user_type) VALUES (?, 'A', ?) RETURNING user_id")) {
            statement.setString(1, "MIGRATION-" + UUID.randomUUID());
            statement.setString(2, type);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static StandardContext insertStandardContext(
            java.sql.Connection connection, long userId, OffsetDateTime now) throws SQLException {
        UUID sessionId = UUID.randomUUID();
        try (var statement = connection.prepareStatement("""
                INSERT INTO standard_sessions
                    (session_id, user_id, token_hash, status, created_at, last_activity_at,
                     expires_at, absolute_expires_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                """)) {
            statement.setObject(1, sessionId);
            statement.setLong(2, userId);
            statement.setBytes(3, UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            statement.setObject(4, now.minusDays(4));
            statement.setObject(5, now.minusDays(3));
            statement.setObject(6, now.plusDays(1));
            statement.setObject(7, now.plusDays(2));
            statement.executeUpdate();
        }
        UUID consentId = UUID.randomUUID();
        try (var statement = connection.prepareStatement("""
                INSERT INTO consent_events
                    (consent_event_id, session_id, user_id, consent_version,
                     consent_document_hash, decision, source, decided_at)
                VALUES (?, ?, ?, 'legacy-v1', ?, 'ACCEPTED', 'STANDARD_WEB', ?)
                """)) {
            statement.setObject(1, consentId);
            statement.setObject(2, sessionId);
            statement.setLong(3, userId);
            statement.setString(4, "a".repeat(64));
            statement.setObject(5, now.minusDays(3));
            statement.executeUpdate();
        }
        return new StandardContext(sessionId, consentId);
    }

    private static UUID insertAnalysis(
            java.sql.Connection connection, long userId, UUID sessionId, UUID consentId,
            String inputType, OffsetDateTime createdAt, String status) throws SQLException {
        UUID analysisId = UUID.randomUUID();
        try (var statement = connection.prepareStatement("""
                INSERT INTO analyses
                    (analysis_id, user_id, session_id, consent_event_id, input_type,
                     status, created_at, completed_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, analysisId);
            statement.setLong(2, userId);
            statement.setObject(3, sessionId);
            statement.setObject(4, consentId);
            statement.setString(5, inputType);
            statement.setString(6, status);
            statement.setObject(7, createdAt);
            statement.setObject(8, "COMPLETED".equals(status) ? createdAt : null);
            statement.setObject(9, createdAt);
            statement.executeUpdate();
        }
        return analysisId;
    }

    private static Map<UUID, AdmissionRow> readAdmissions(java.sql.Connection connection) throws SQLException {
        Map<UUID, AdmissionRow> result = new HashMap<>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT admission_id, analysis_id, user_id, input_type, actor_mode, state,
                               reserved_at, expires_at, accepted_at, released_at
                        FROM analysis_admissions
                        """)) {
            while (rows.next()) {
                UUID analysisId = rows.getObject("analysis_id", UUID.class);
                result.put(analysisId, new AdmissionRow(
                        rows.getObject("admission_id", UUID.class), analysisId,
                        rows.getLong("user_id"), rows.getString("input_type"),
                        rows.getString("actor_mode"), rows.getString("state"),
                        rows.getObject("reserved_at", OffsetDateTime.class),
                        rows.getObject("expires_at", OffsetDateTime.class),
                        rows.getObject("accepted_at", OffsetDateTime.class),
                        rows.getObject("released_at", OffsetDateTime.class)));
            }
        }
        return result;
    }

    private static void assertBackfill(
            AdmissionRow row, UUID analysisId, long userId, String inputType,
            String actorMode, OffsetDateTime createdAt) {
        assertThat(row).isNotNull();
        assertThat(row.admissionId()).isEqualTo(analysisId);
        assertThat(row.analysisId()).isEqualTo(analysisId);
        assertThat(row.userId()).isEqualTo(userId);
        assertThat(row.inputType()).isEqualTo(inputType);
        assertThat(row.actorMode()).isEqualTo(actorMode);
        assertThat(row.state()).isEqualTo("ACCEPTED");
        assertThat(row.reservedAt()).isEqualTo(createdAt);
        assertThat(row.acceptedAt()).isEqualTo(createdAt);
        assertThat(row.expiresAt()).isEqualTo(createdAt.plusMinutes(8));
        assertThat(row.releasedAt()).isNull();
    }

    private static void insertDuplicateAnalysisReference(
            java.sql.Connection connection, UUID analysisId, long userId, OffsetDateTime now) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO analysis_admissions
                    (admission_id, user_id, analysis_id, input_type, actor_mode, state,
                     reserved_at, expires_at, accepted_at)
                VALUES (?, ?, ?, 'TEXT', 'ADMIN_JWT', 'ACCEPTED', ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setLong(2, userId);
            statement.setObject(3, analysisId);
            statement.setObject(4, now);
            statement.setObject(5, now.plusMinutes(8));
            statement.setObject(6, now);
            statement.executeUpdate();
        }
    }

    private static void insertInvalidAcceptedAdmission(
            java.sql.Connection connection, long userId, OffsetDateTime now) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO analysis_admissions
                    (admission_id, user_id, input_type, actor_mode, state,
                     reserved_at, expires_at, accepted_at)
                VALUES (?, ?, 'TEXT', 'ADMIN_JWT', 'ACCEPTED', ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setLong(2, userId);
            statement.setObject(3, now);
            statement.setObject(4, now.plusMinutes(8));
            statement.setObject(5, now);
            statement.executeUpdate();
        }
    }

    private record StandardContext(UUID sessionId, UUID consentId) {
    }

    private record AdmissionRow(
            UUID admissionId, UUID analysisId, long userId, String inputType,
            String actorMode, String state, OffsetDateTime reservedAt,
            OffsetDateTime expiresAt, OffsetDateTime acceptedAt, OffsetDateTime releasedAt) {
    }
}
