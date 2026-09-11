package com.mnk.identipatia;

import com.mnk.identipatia.dto.StandardUserRegistrationRequest;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class IdentipatIaApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoads() {
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

}
