package com.mnk.identipatia.mapper;

import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.model.User;
import org.junit.jupiter.api.Test;

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
        dto.setCurrencyOfIncome("PEN");
        dto.setMonthlyIncome(3200.0);

        // WHEN
        User entity = userMapper.toEntity(dto);

        // THEN
        assertNotNull(entity);
        assertEquals(dto.getUserId(), entity.getUserId());
        assertEquals(dto.getFirstName(), entity.getFirstName());
        assertEquals(dto.getPaternalLastName(), entity.getPaternalLastName());
        assertEquals(dto.getMaternalLastName(), entity.getMaternalLastName());
        assertEquals(dto.getCurrencyOfIncome(), entity.getCurrencyOfIncome());
        assertEquals(dto.getMonthlyIncome(), entity.getMonthlyIncome());

    }

    @Test
    void testToDto() {
        // GIVEN
        User entity = new User();
        entity.setUserId(2L);
        entity.setFirstName("Ana");
        entity.setPaternalLastName("Torres");
        entity.setMaternalLastName("Salas");
        entity.setCurrencyOfIncome("USD");
        entity.setMonthlyIncome(5000.0);
        entity.setCreationDate(LocalDateTime.now());

        // WHEN
        UserDTO dto = userMapper.toDto(entity);

        // THEN
        assertNotNull(dto);
        assertEquals(entity.getUserId(), dto.getUserId());
        assertEquals(entity.getFirstName(), dto.getFirstName());
        assertEquals(entity.getPaternalLastName(), dto.getPaternalLastName());
        assertEquals(entity.getMaternalLastName(), dto.getMaternalLastName());
        assertEquals(entity.getCurrencyOfIncome(), dto.getCurrencyOfIncome());
        assertEquals(entity.getMonthlyIncome(), dto.getMonthlyIncome());
    }
}
