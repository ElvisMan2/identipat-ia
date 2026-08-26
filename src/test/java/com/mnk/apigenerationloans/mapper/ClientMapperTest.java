package com.mnk.identipatia.mapper;

import com.mnk.identipatia.dto.ClientDTO;
import com.mnk.identipatia.model.Client;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ClientMapperTest {

    private final ClientMapper clientMapper = new ClientMapperImpl();

    @Test
    void testToEntity() {
        // GIVEN
        ClientDTO dto = new ClientDTO();
        dto.setClientId(1L);
        dto.setFirstName("Luis");
        dto.setPaternalLastName("Fernández");
        dto.setMaternalLastName("Ramos");
        dto.setCurrencyOfIncome("PEN");
        dto.setMonthlyIncome(3200.0);

        // WHEN
        Client entity = clientMapper.toEntity(dto);

        // THEN
        assertNotNull(entity);
        assertEquals(dto.getClientId(), entity.getClientId());
        assertEquals(dto.getFirstName(), entity.getFirstName());
        assertEquals(dto.getPaternalLastName(), entity.getPaternalLastName());
        assertEquals(dto.getMaternalLastName(), entity.getMaternalLastName());
        assertEquals(dto.getCurrencyOfIncome(), entity.getCurrencyOfIncome());
        assertEquals(dto.getMonthlyIncome(), entity.getMonthlyIncome());

    }

    @Test
    void testToDto() {
        // GIVEN
        Client entity = new Client();
        entity.setClientId(2L);
        entity.setFirstName("Ana");
        entity.setPaternalLastName("Torres");
        entity.setMaternalLastName("Salas");
        entity.setCurrencyOfIncome("USD");
        entity.setMonthlyIncome(5000.0);
        entity.setCreationDate(LocalDateTime.now());

        // WHEN
        ClientDTO dto = clientMapper.toDto(entity);

        // THEN
        assertNotNull(dto);
        assertEquals(entity.getClientId(), dto.getClientId());
        assertEquals(entity.getFirstName(), dto.getFirstName());
        assertEquals(entity.getPaternalLastName(), dto.getPaternalLastName());
        assertEquals(entity.getMaternalLastName(), dto.getMaternalLastName());
        assertEquals(entity.getCurrencyOfIncome(), dto.getCurrencyOfIncome());
        assertEquals(entity.getMonthlyIncome(), dto.getMonthlyIncome());
    }
}

