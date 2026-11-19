package com.mnk.identipatia.reporting;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportingMigrationTest {
    @Test
    void migrationsPreserveHistoricalContextAndAllowOnlyReportVersions10And11() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("identipat_reporting_migration_test")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("4"))
                    .load().migrate();

            UUID completed = UUID.randomUUID();
            UUID pending = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword());
                    var statement = connection.createStatement()) {
                ResultSet user = statement.executeQuery("""
                        insert into users(doi, status, user_type) values ('migration-admin', 'A', 'ADMIN')
                        returning user_id
                        """);
                user.next();
                long userId = user.getLong(1);
                statement.executeUpdate("""
                        insert into analyses(analysis_id,user_id,session_id,consent_event_id,input_type,status,
                            created_at,completed_at,updated_at)
                        values ('%s',%d,null,null,'TEXT','COMPLETED',current_timestamp,current_timestamp,current_timestamp)
                        """.formatted(completed, userId));
                statement.executeUpdate("""
                        insert into analyses(analysis_id,user_id,session_id,consent_event_id,input_type,status,
                            created_at,updated_at)
                        values ('%s',%d,null,null,'TEXT','RECEIVED',current_timestamp,current_timestamp)
                        """.formatted(pending, userId));
            }

            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();

            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword());
                    var statement = connection.createStatement()) {
                ResultSet backfill = statement.executeQuery("""
                        select c.analysis_id, c.disclaimer_id, c.report_version, c.created_at, a.completed_at
                        from analysis_report_contexts c join analyses a using (analysis_id)
                        """);
                assertThat(backfill.next()).isTrue();
                assertThat(backfill.getObject("analysis_id", UUID.class)).isEqualTo(completed);
                assertThat(backfill.getObject("disclaimer_id", UUID.class))
                        .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
                assertThat(backfill.getString("report_version")).isEqualTo("diagnostic-report/1.0");
                assertThat(backfill.getTimestamp("created_at")).isEqualTo(backfill.getTimestamp("completed_at"));
                assertThat(backfill.next()).isFalse();
                ResultSet contextCount = statement.executeQuery("select count(*) from analysis_report_contexts");
                assertThat(contextCount.next()).isTrue();
                assertThat(contextCount.getInt(1)).isEqualTo(1);
                ResultSet constraint = statement.executeQuery("""
                        select pg_get_constraintdef(oid) as definition from pg_constraint
                        where conname = 'ck_analysis_report_contexts_version'
                        """);
                assertThat(constraint.next()).isTrue();
                assertThat(constraint.getString("definition"))
                        .contains("diagnostic-report/1.0", "diagnostic-report/1.1")
                        .doesNotContain("diagnostic-report/9.9");
                ResultSet seed = statement.executeQuery("""
                        select disclaimer_id, version, title, content, content_sha256, status from report_disclaimers
                        """);
                assertThat(seed.next()).isTrue();
                assertThat(seed.getObject("disclaimer_id", UUID.class))
                        .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
                assertThat(seed.getLong("version")).isEqualTo(1L);
                assertThat(seed.getString("title")).isEqualTo("Importante");
                assertThat(seed.getString("content")).contains("No reemplaza, bajo ninguna circunstancia");
                assertThat(seed.getString("content_sha256"))
                        .isEqualTo("4ec5ec3db0098b29667c1e3ba03359bfc1bc46542b0931dfb19937775e128967");
                assertThat(seed.getString("status")).isEqualTo("ACTIVE");
                assertThat(seed.next()).isFalse();

                long userId;
                try (ResultSet user = statement.executeQuery("select user_id from users where doi='migration-admin'")) {
                    user.next();
                    userId = user.getLong(1);
                }
                UUID legacy = UUID.randomUUID();
                UUID v3 = UUID.randomUUID();
                UUID unsupported = UUID.randomUUID();
                try (PreparedStatement insertAnalysis = connection.prepareStatement("""
                        insert into analyses(analysis_id,user_id,session_id,consent_event_id,input_type,status,
                            created_at,completed_at,updated_at)
                        values (?, ?, null, null, 'TEXT', 'COMPLETED', current_timestamp,
                            current_timestamp, current_timestamp)
                        """)) {
                    for (UUID id : new UUID[] {legacy, v3, unsupported}) {
                        insertAnalysis.setObject(1, id);
                        insertAnalysis.setLong(2, userId);
                        insertAnalysis.executeUpdate();
                    }
                }
                try (PreparedStatement insertContext = connection.prepareStatement("""
                        insert into analysis_report_contexts(analysis_id, disclaimer_id, report_version, created_at)
                        values (?, '00000000-0000-0000-0000-000000000001', ?, current_timestamp)
                        """)) {
                    insertContext.setObject(1, legacy);
                    insertContext.setString(2, "diagnostic-report/1.0");
                    assertThat(insertContext.executeUpdate()).isEqualTo(1);
                    insertContext.setObject(1, v3);
                    insertContext.setString(2, "diagnostic-report/1.1");
                    assertThat(insertContext.executeUpdate()).isEqualTo(1);
                    insertContext.setObject(1, unsupported);
                    insertContext.setString(2, "diagnostic-report/9.9");
                    assertThatThrownBy(insertContext::executeUpdate).isInstanceOf(SQLException.class);
                }
                try (var verify = connection.prepareStatement("""
                        select report_version from analysis_report_contexts where analysis_id = ?
                        """)) {
                    verify.setObject(1, completed);
                    try (ResultSet historic = verify.executeQuery()) {
                        assertThat(historic.next()).isTrue();
                        assertThat(historic.getString(1)).isEqualTo("diagnostic-report/1.0");
                    }
                    verify.setObject(1, legacy);
                    try (ResultSet context = verify.executeQuery()) {
                        assertThat(context.next()).isTrue();
                        assertThat(context.getString(1)).isEqualTo("diagnostic-report/1.0");
                    }
                    verify.setObject(1, v3);
                    try (ResultSet context = verify.executeQuery()) {
                        assertThat(context.next()).isTrue();
                        assertThat(context.getString(1)).isEqualTo("diagnostic-report/1.1");
                    }
                    verify.setObject(1, unsupported);
                    try (ResultSet context = verify.executeQuery()) {
                        assertThat(context.next()).isFalse();
                    }
                }
                ResultSet sequence = statement.executeQuery("select nextval('report_disclaimer_version_seq')");
                sequence.next();
                assertThat(sequence.getLong(1)).isEqualTo(2L);
            }
        }
    }
}
