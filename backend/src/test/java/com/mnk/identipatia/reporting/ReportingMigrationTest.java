package com.mnk.identipatia.reporting;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingMigrationTest {
    @Test
    void v5BackfillsCompletedAnalysesAndLeavesOtherStatusesWithoutContext() throws Exception {
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
                ResultSet backfill = statement.executeQuery("select analysis_id, disclaimer_id, report_version "
                        + "from analysis_report_contexts");
                assertThat(backfill.next()).isTrue();
                assertThat(backfill.getObject("analysis_id", UUID.class)).isEqualTo(completed);
                assertThat(backfill.getObject("disclaimer_id", UUID.class))
                        .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
                assertThat(backfill.getString("report_version")).isEqualTo("diagnostic-report/1.0");
                assertThat(backfill.next()).isFalse();
                ResultSet sequence = statement.executeQuery("select nextval('report_disclaimer_version_seq')");
                sequence.next();
                assertThat(sequence.getLong(1)).isEqualTo(2L);
            }
        }
    }
}
