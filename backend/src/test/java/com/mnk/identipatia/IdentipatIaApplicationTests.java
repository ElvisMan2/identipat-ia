package com.mnk.identipatia;

import com.mnk.identipatia.dto.StandardUserRegistrationRequest;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.env.Environment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IdentipatIaApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_test");

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayAppliesV1ToFreshTestcontainerAndHibernateValidates() {
        Integer usersTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'users'
                """, Integer.class);
        Integer historyTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'
                """, Integer.class);
        Map<String, Object> migration = jdbcTemplate.queryForMap("""
                SELECT installed_rank, version, description, type, success
                FROM flyway_schema_history
                WHERE version = '1'
                """);

        assertEquals(1, usersTableCount);
        assertEquals(1, historyTableCount);
        assertEquals(1, migration.get("installed_rank"));
        assertEquals("1", migration.get("version"));
        assertEquals("baseline schema", migration.get("description"));
        assertEquals("SQL", migration.get("type"));
        assertEquals(true, migration.get("success"));
        assertEquals("validate", environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto"));
        assertFalse(environment.getRequiredProperty("spring.flyway.baseline-on-migrate", Boolean.class));
    }

    @Test
    void integrationDatasourceUsesDynamicTestcontainerPort() throws SQLException {
        String jdbcUrl;
        try (Connection connection = dataSource.getConnection()) {
            jdbcUrl = connection.getMetaData().getURL();
        }

        System.out.printf("TESTCONTAINERS_POSTGRES host=%s mappedPort=%d jdbcUrl=%s%n",
                POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), jdbcUrl);
        assertTrue(POSTGRES.isRunning());
        assertTrue(jdbcUrl.contains(POSTGRES.getHost()));
        assertTrue(jdbcUrl.contains(":" + POSTGRES.getFirstMappedPort() + "/"));
        assertFalse(jdbcUrl.contains("localhost:5433"));
    }

    @Test
    void testProfileProvidesDeterministicJwtConfiguration() {
        assertTrue(List.of(environment.getActiveProfiles()).contains("test"));
        assertEquals("identipat-test-secret-key-only-for-automated-tests",
                environment.getRequiredProperty("app.jwt.secret"));
        assertEquals(3600000L,
                environment.getRequiredProperty("app.jwt.expiration-ms", Long.class));
    }

    @Test
    @Transactional
    void standardRegistrationPersistsWithoutPassword() {
        String doi = "F03-" + UUID.randomUUID();
        StandardUserRegistrationRequest request = new StandardUserRegistrationRequest();
        request.setFirstName("Prueba");
        request.setPaternalLastName("Seguridad");
        request.setMaternalLastName("F03");
        request.setDoi(doi);
        request.setDoiType("DNI");
        request.setBirthDate(LocalDate.of(1990, 1, 1));
        request.setGender("FEMALE");
        request.setEmail("f03-" + UUID.randomUUID() + "@example.com");
        request.setPhone("016543210");
        request.setMobilePhone("912345678");
        request.setProfession("Prueba");

        userService.registerStandard(request);

        User savedUser = userRepository.findByDoi(doi).orElseThrow();
        assertEquals("STANDARD", savedUser.getUserType());
        assertEquals("A", savedUser.getStatus());
        assertNull(savedUser.getPassword());
    }

    @Test
    @Transactional
    void databaseKeepsCurrentUniqueConstraintOnDoi() {
        String doi = "F05-" + UUID.randomUUID();
        User first = minimalUser(doi);
        User duplicate = minimalUser(doi);

        userRepository.saveAndFlush(first);

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(duplicate));
    }

    private static User minimalUser(String doi) {
        User user = new User();
        user.setDoi(doi);
        user.setStatus("A");
        return user;
    }

}
