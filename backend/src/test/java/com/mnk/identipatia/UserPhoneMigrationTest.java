package com.mnk.identipatia;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserPhoneMigrationTest {

    @Test
    void v13DropsOnlyPhoneAndPreservesExistingUserAndMobilePhone() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("identipat_user_phone_migration_test")) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("12"))
                    .load()
                    .migrate();

            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = connection.createStatement()) {
                long userId;
                try (var insertedUser = statement.executeQuery("""
                        INSERT INTO users
                            (doi, status, user_type, first_name, phone, mobile_phone, email)
                        VALUES
                            ('MIGRATION-PHONE-1', 'A', 'STANDARD', 'Ana', '016543210',
                             '912345678', 'ana@example.test')
                        RETURNING user_id
                        """)) {
                    assertThat(insertedUser.next()).isTrue();
                    userId = insertedUser.getLong("user_id");
                }
                statement.executeUpdate("""
                        INSERT INTO standard_sessions
                            (session_id, user_id, token_hash, status, created_at, last_activity_at,
                             expires_at, absolute_expires_at)
                        VALUES
                            ('00000000-0000-0000-0000-000000000013', %d, decode('1301', 'hex'),
                             'ACTIVE', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z',
                             '2026-01-01T01:00:00Z', '2026-01-02T00:00:00Z')
                        """.formatted(userId));
                statement.executeUpdate("""
                        INSERT INTO consent_events
                            (consent_event_id, session_id, user_id, consent_version,
                             consent_document_hash, decision, source, decided_at)
                        VALUES
                            ('00000000-0000-0000-0000-000000001013',
                             '00000000-0000-0000-0000-000000000013', %d, 'migration-test',
                             'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                             'ACCEPTED', 'TEST', '2026-01-01T00:00:00Z')
                        """.formatted(userId));

                Flyway.configure()
                        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();

                try (var columns = statement.executeQuery("""
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'users'
                        """)) {
                    Set<String> columnNames = new HashSet<>();
                    while (columns.next()) {
                        columnNames.add(columns.getString("column_name"));
                    }
                    assertThat(columnNames)
                            .contains("mobile_phone", "email", "doi", "status", "user_type")
                            .doesNotContain("phone");
                }

                try (var user = statement.executeQuery("""
                        SELECT doi, status, user_type, first_name, mobile_phone, email
                        FROM users
                        WHERE doi = 'MIGRATION-PHONE-1'
                        """)) {
                    assertThat(user.next()).isTrue();
                    assertThat(user.getString("doi")).isEqualTo("MIGRATION-PHONE-1");
                    assertThat(user.getString("status")).isEqualTo("A");
                    assertThat(user.getString("user_type")).isEqualTo("STANDARD");
                    assertThat(user.getString("first_name")).isEqualTo("Ana");
                    assertThat(user.getString("mobile_phone")).isEqualTo("912345678");
                    assertThat(user.getString("email")).isEqualTo("ana@example.test");
                    assertThat(user.next()).isFalse();
                }

                try (var relations = statement.executeQuery("""
                        SELECT s.user_id, c.consent_version
                        FROM standard_sessions s
                        JOIN consent_events c
                          ON c.session_id = s.session_id AND c.user_id = s.user_id
                        WHERE s.session_id = '00000000-0000-0000-0000-000000000013'
                        """)) {
                    assertThat(relations.next()).isTrue();
                    assertThat(relations.getLong("user_id")).isEqualTo(userId);
                    assertThat(relations.getString("consent_version")).isEqualTo("migration-test");
                    assertThat(relations.next()).isFalse();
                }
            }
        }
    }
}
