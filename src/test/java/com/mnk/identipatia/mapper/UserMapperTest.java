package com.mnk.identipatia.mapper;

import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    private final UserMapper userMapper = new UserMapperImpl();

    @Test
    void testToEntity() {
        // GIVEN
        UserDTO dto = new UserDTO();
        dto.setUserId(1L);
        dto.setFirstName("Luis");
        dto.setPaternalLastName("Fernández");
        dto.setMaternalLastName("Ramos");
        dto.setDoi("12345678");
        dto.setDoiType("DNI");
        dto.setBirthDate(LocalDate.of(1990, 5, 20));
        dto.setGender("MALE");
        dto.setEmail("luis.fernandez@example.com");
        dto.setPhone("014567890");
        dto.setMobilePhone("987654321");
        dto.setUserType("ADMIN");
        dto.setProfession("Ingeniero");

        // WHEN
        User entity = userMapper.toEntity(dto);

        // THEN
        assertNotNull(entity);
        assertEquals(dto.getUserId(), entity.getUserId());
        assertEquals(dto.getFirstName(), entity.getFirstName());
        assertEquals(dto.getPaternalLastName(), entity.getPaternalLastName());
        assertEquals(dto.getMaternalLastName(), entity.getMaternalLastName());
        assertEquals(dto.getDoi(), entity.getDoi());
        assertEquals(dto.getDoiType(), entity.getDoiType());
        assertEquals(dto.getBirthDate(), entity.getBirthDate());
        assertEquals(dto.getGender(), entity.getGender());
        assertEquals(dto.getEmail(), entity.getEmail());
        assertEquals(dto.getPhone(), entity.getPhone());
        assertEquals(dto.getMobilePhone(), entity.getMobilePhone());
        assertEquals(dto.getUserType(), entity.getUserType());
        assertEquals(dto.getProfession(), entity.getProfession());

    }

    @Test
    void testToDto() {
        // GIVEN
        User entity = new User();
        entity.setUserId(2L);
        entity.setFirstName("Ana");
        entity.setPaternalLastName("Torres");
        entity.setMaternalLastName("Salas");
        entity.setDoi("87654321");
        entity.setDoiType("CE");
        entity.setBirthDate(LocalDate.of(1985, 3, 15));
        entity.setGender("FEMALE");
        entity.setEmail("ana.torres@example.com");
        entity.setPhone("016543210");
        entity.setMobilePhone("912345678");
        entity.setUserType("STANDARD");
        entity.setProfession("Abogada");
        entity.setCreationDate(LocalDateTime.now());

        // WHEN
        UserDTO dto = userMapper.toDto(entity);

        // THEN
        assertNotNull(dto);
        assertEquals(entity.getUserId(), dto.getUserId());
        assertEquals(entity.getFirstName(), dto.getFirstName());
        assertEquals(entity.getPaternalLastName(), dto.getPaternalLastName());
        assertEquals(entity.getMaternalLastName(), dto.getMaternalLastName());
        assertEquals(entity.getDoi(), dto.getDoi());
        assertEquals(entity.getDoiType(), dto.getDoiType());
        assertEquals(entity.getBirthDate(), dto.getBirthDate());
        assertEquals(entity.getGender(), dto.getGender());
        assertEquals(entity.getEmail(), dto.getEmail());
        assertEquals(entity.getPhone(), dto.getPhone());
        assertEquals(entity.getMobilePhone(), dto.getMobilePhone());
        assertEquals(entity.getUserType(), dto.getUserType());
        assertEquals(entity.getProfession(), dto.getProfession());
    }
}
