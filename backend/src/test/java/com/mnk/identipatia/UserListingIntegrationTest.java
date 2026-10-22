package com.mnk.identipatia;

import com.mnk.identipatia.dto.PagedResponse;
import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.UserSearchCriteria;
import com.mnk.identipatia.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class UserListingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_user_listing_test");

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void seedUsers() {
        userRepository.saveAllAndFlush(List.of(
                user("Jose", "Zuniga", "Lima", "10000001", "DNI", "jzuniga@example.test", "Ingeniero", "STANDARD", "A", 1),
                user("Lucia", "Ramos", "Vega", "CE-2002", "CE", "lucia@example.test", "Abogada", "ADMIN", "I", 2),
                user("Carlos", "Mendoza", "Ruiz", "10000003", "DNI", "carlos@example.test", "Arquitecto", "STANDARD", "I", 3),
                user("Elena", "Torres", "Diaz", "CE-2004", "CE", "elena@example.test", "Medica", "ADMIN", "A", 4),
                user("Bruno", "Silva", "Costa", "10000005", "DNI", "bruno@example.test", "Biologo", "STANDARD", "A", 5),
                user("Ana", "Abarca", "Soto", "CE-2006", "CE", "ana@example.test", "Docente", "STANDARD", "A", 6)));
    }

    @Test
    void paginatesFirstSecondAndValidEmptyPagesWithCorrectTotals() {
        PagedResponse<UserDTO> first = find(0, 2, null, null, null, null, "userId,asc");
        PagedResponse<UserDTO> second = find(1, 2, null, null, null, null, "userId,asc");
        PagedResponse<UserDTO> empty = find(3, 2, null, null, null, null, "userId,asc");

        assertThat(first.content()).extracting(UserDTO::getFirstName).containsExactly("Jose", "Lucia");
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(6);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.first()).isTrue();
        assertThat(first.last()).isFalse();

        assertThat(second.content()).extracting(UserDTO::getFirstName).containsExactly("Carlos", "Elena");
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.totalElements()).isEqualTo(6);

        assertThat(empty.content()).isEmpty();
        assertThat(empty.page()).isEqualTo(3);
        assertThat(empty.totalElements()).isEqualTo(6);
        assertThat(empty.totalPages()).isEqualTo(3);
        assertThat(empty.last()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("searchCases")
    void searchesEverySupportedFieldCaseInsensitively(String search, String expectedDoi) {
        PagedResponse<UserDTO> response = find(0, 20, search, null, null, null, "userId,asc");

        assertThat(response.content()).extracting(UserDTO::getDoi).containsExactly(expectedDoi);
    }

    static Stream<Arguments> searchCases() {
        return Stream.of(
                Arguments.of("jose", "10000001"),
                Arguments.of("ZUNIGA", "10000001"),
                Arguments.of("lima", "10000001"),
                Arguments.of("10000001", "10000001"),
                Arguments.of("JZUNIGA@EXAMPLE.TEST", "10000001"),
                Arguments.of("ingeniero", "10000001"));
    }

    @Test
    void combinesMultipleSearchTermsEvenWhenTheyAreInDifferentColumns() {
        PagedResponse<UserDTO> response = find(
                0, 20, "  JOSE   zuniga ", null, null, null, "userId,asc");

        assertThat(response.content()).extracting(UserDTO::getDoi).containsExactly("10000001");
    }

    @ParameterizedTest
    @MethodSource("filterCases")
    void filtersByCanonicalDomainValues(
            String userType,
            String status,
            String doiType,
            long expectedCount) {
        PagedResponse<UserDTO> response = find(
                0, 20, null, userType, status, doiType, "userId,asc");

        assertThat(response.totalElements()).isEqualTo(expectedCount);
    }

    static Stream<Arguments> filterCases() {
        return Stream.of(
                Arguments.of("admin", null, null, 2L),
                Arguments.of("STANDARD", null, null, 4L),
                Arguments.of(null, "a", null, 4L),
                Arguments.of(null, "I", null, 2L),
                Arguments.of(null, null, "dni", 3L),
                Arguments.of(null, null, "CE", 3L));
    }

    @Test
    void combinesSearchAndAllFiltersWithAndSemantics() {
        PagedResponse<UserDTO> response = find(
                0, 20, "ingeniero jose", " standard ", " a ", " dni ", "userId,asc");

        assertThat(response.content()).extracting(UserDTO::getDoi).containsExactly("10000001");
    }

    @Test
    void sortsAscendingAndDescendingWithStableUserIdTieBreaker() {
        PagedResponse<UserDTO> ascending = find(0, 20, null, null, null, null, "firstName,asc");
        PagedResponse<UserDTO> descending = find(0, 20, null, null, null, null, "firstName,desc");
        UserSearchCriteria criteria = UserSearchCriteria.of(
                0, 20, null, null, null, null, "firstName,asc");

        assertThat(ascending.content()).extracting(UserDTO::getFirstName)
                .containsExactly("Ana", "Bruno", "Carlos", "Elena", "Jose", "Lucia");
        assertThat(descending.content()).extracting(UserDTO::getFirstName)
                .containsExactly("Lucia", "Jose", "Elena", "Carlos", "Bruno", "Ana");
        assertThat(criteria.toPageable().getSort().getOrderFor("userId")).isNotNull();
    }

    @Test
    void csvExportReturnsEveryMatchWithoutTheListingPageLimit() {
        List<User> additionalUsers = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            additionalUsers.add(user(
                    "Bulk" + index,
                    "Apellido" + index,
                    "Prueba",
                    "BULK-" + index,
                    index % 2 == 0 ? "DNI" : "CE",
                    "bulk" + index + "@example.test",
                    "Profesional",
                    "STANDARD",
                    "A",
                    7 + index));
        }
        userRepository.saveAllAndFlush(additionalUsers);

        String csv = export(null, null, null, null, "userId,asc");

        assertThat(csv.split("\\r\\n", -1)).hasSize(28);
        assertThat(csv).contains("\"Bulk20 Apellido20 Prueba\"");
        assertThat(csv).doesNotContain("encoded-sensitive-value");
    }

    @Test
    void csvExportReusesSearchAndEveryFilterWithAndSemantics() {
        String searchCsv = export("ingeniero jose", null, null, null, "userId,asc");
        String userTypeCsv = export(null, "ADMIN", null, null, "userId,asc");
        String statusCsv = export(null, null, "I", null, "userId,asc");
        String doiTypeCsv = export(null, null, null, "CE", "userId,asc");
        String combinedCsv = export(
                "ingeniero jose", "STANDARD", "A", "DNI", "userId,asc");

        assertThat(searchCsv).contains("Jose Zuniga Lima").doesNotContain("Lucia Ramos Vega");
        assertThat(userTypeCsv).contains("Lucia Ramos Vega", "Elena Torres Diaz")
                .doesNotContain("Jose Zuniga Lima");
        assertThat(statusCsv).contains("Lucia Ramos Vega", "Carlos Mendoza Ruiz")
                .doesNotContain("Elena Torres Diaz");
        assertThat(doiTypeCsv).contains("Lucia Ramos Vega", "Elena Torres Diaz", "Ana Abarca Soto")
                .doesNotContain("Jose Zuniga Lima");
        assertThat(combinedCsv).contains("Jose Zuniga Lima").doesNotContain("Lucia Ramos Vega");
    }

    @Test
    void csvExportHonorsAscendingDescendingAndStableUserIdTieBreaker() {
        User firstTwin = userRepository.saveAndFlush(user(
                "Twin", "First", "User", "TWIN-1", "DNI", "twin1@example.test",
                "Tester", "STANDARD", "A", 20));
        User secondTwin = userRepository.saveAndFlush(user(
                "Twin", "Second", "User", "TWIN-2", "DNI", "twin2@example.test",
                "Tester", "STANDARD", "A", 21));

        String ascending = export(null, null, null, null, "firstName,asc");
        String descending = export(null, null, null, null, "firstName,desc");

        assertThat(ascending.indexOf("Ana Abarca Soto"))
                .isLessThan(ascending.indexOf("Twin First User"));
        assertThat(descending.indexOf("Twin First User"))
                .isLessThan(descending.indexOf("Ana Abarca Soto"));
        assertThat(ascending.indexOf("\"" + firstTwin.getUserId() + "\""))
                .isLessThan(ascending.indexOf("\"" + secondTwin.getUserId() + "\""));
        assertThat(descending.indexOf("\"" + firstTwin.getUserId() + "\""))
                .isLessThan(descending.indexOf("\"" + secondTwin.getUserId() + "\""));
    }

    @Test
    void csvExportWithNoMatchesContainsOnlyBomAndHeader() {
        String csv = export("no-user-can-match-this", null, null, null, "userId,asc");

        assertThat(csv).isEqualTo("\uFEFF\"ID\",\"Nombre completo\",\"Documento\",\"Nacimiento\",\"Género\",\"Correo\",\"Teléfono\",\"Celular\",\"Tipo\",\"Profesión\",\"Estado\"");
    }

    private PagedResponse<UserDTO> find(
            int page,
            int size,
            String search,
            String userType,
            String status,
            String doiType,
            String sort) {
        return userService.findAll(UserSearchCriteria.of(
                page, size, search, userType, status, doiType, sort));
    }

    private String export(
            String search,
            String userType,
            String status,
            String doiType,
            String sort) {
        byte[] csv = userService.exportCsv(UserSearchCriteria.forExport(
                search, userType, status, doiType, sort));
        return new String(csv, StandardCharsets.UTF_8);
    }

    private static User user(
            String firstName,
            String paternalLastName,
            String maternalLastName,
            String doi,
            String doiType,
            String email,
            String profession,
            String userType,
            String status,
            int day) {
        User user = new User();
        user.setFirstName(firstName);
        user.setPaternalLastName(paternalLastName);
        user.setMaternalLastName(maternalLastName);
        user.setDoi(doi);
        user.setDoiType(doiType);
        user.setBirthDate(LocalDate.of(1990, 1, day));
        user.setGender("FEMALE");
        user.setEmail(email);
        user.setPhone("016543210");
        user.setMobilePhone("912345678");
        user.setUserType(userType);
        user.setProfession(profession);
        user.setStatus(status);
        user.setCreationDate(LocalDateTime.of(2026, 1, day, 10, 0));
        user.setPassword("encoded-sensitive-value");
        return user;
    }
}
