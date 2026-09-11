package com.mnk.identipatia;

import com.mnk.identipatia.dto.StandardUserRegistrationRequest;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
        assertTrue(environment.getRequiredProperty("springdoc.api-docs.enabled", Boolean.class));
        assertTrue(environment.getRequiredProperty("springdoc.swagger-ui.enabled", Boolean.class));
    }

    @Test
    void openApiDocumentationExposesTheCurrentContractWithoutPublicSensitiveSchemas() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        JsonNode api = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(api.path("openapi").asText()).startsWith("3.");
        assertThat(api.at("/paths/~1users~1identify/post").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1users/post").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1users~1login/post").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1users/get").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1users~1{userId}/delete").isMissingNode()).isFalse();

        assertThat(api.at("/paths/~1users~1identify/post/security").isMissingNode()).isTrue();
        assertThat(api.at("/paths/~1users/post/security").isMissingNode()).isTrue();
        assertThat(api.at("/paths/~1users~1login/post/security").isMissingNode()).isTrue();
        assertThat(api.at("/paths/~1users/get/security/0/bearerAuth").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1users/get/responses/200/content/application~1json/schema/type").asText()).isEqualTo("array");
        assertThat(api.at("/paths/~1users~1{userId}/get/security/0/bearerAuth").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1users~1doi~1{doi}/get/security/0/bearerAuth").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1users~1admin~1{userId}/put/security/0/bearerAuth").isMissingNode()).isFalse();

        assertThat(api.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(api.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(api.at("/components/securitySchemes/bearerAuth/bearerFormat").asText()).isEqualTo("JWT");

        JsonNode recognitionProperties = api.at("/components/schemas/DocumentRecognitionResponse/properties");
        assertThat(recognitionProperties.size()).isEqualTo(1);
        assertThat(recognitionProperties.has("registered")).isTrue();

        JsonNode registrationProperties = api.at("/components/schemas/StandardUserRegistrationRequest/properties");
        assertThat(registrationProperties.has("userType")).isFalse();
        assertThat(registrationProperties.has("status")).isFalse();
        assertThat(registrationProperties.has("password")).isFalse();

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
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
