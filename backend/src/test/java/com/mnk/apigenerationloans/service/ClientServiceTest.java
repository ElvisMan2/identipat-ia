package com.mnk.identipatia.service;

import com.mnk.identipatia.dto.ClientDTO;
import com.mnk.identipatia.exception.ClientNotFoundException;
import com.mnk.identipatia.mapper.ClientMapper;
import com.mnk.identipatia.model.Client;
import com.mnk.identipatia.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientMapper clientMapper;

    @InjectMocks
    private ClientService clientService;

    @Test
    void createIgnoresClientIdAndSetsCreationDate() {
        ClientDTO request = clientDTO(99L, "Ana", "Torres", "Salas", "PEN", 5000.0);
        Client client = client();
        Client savedClient = client();
        savedClient.setClientId(1L);
        ClientDTO expected = clientDTO(1L, "Ana", "Torres", "Salas", "PEN", 5000.0);

        when(clientMapper.toEntity(request)).thenReturn(client);
        when(clientRepository.save(client)).thenReturn(savedClient);
        when(clientMapper.toDto(savedClient)).thenReturn(expected);

        ClientDTO result = clientService.create(request);

        assertEquals(expected, result);
        assertEquals(null, client.getClientId());
        assertNotNull(client.getCreationDate());
        verify(clientRepository).save(client);
        verify(clientMapper).toDto(savedClient);
    }

    @Test
    void findAllMapsEveryClient() {
        Client first = client();
        Client second = client();
        ClientDTO firstDTO = clientDTO(1L, "Ana", "Torres", "Salas", "PEN", 5000.0);
        ClientDTO secondDTO = clientDTO(2L, "Luis", "Ramos", "Diaz", "USD", 3200.0);

        when(clientRepository.findAll()).thenReturn(List.of(first, second));
        when(clientMapper.toDto(first)).thenReturn(firstDTO);
        when(clientMapper.toDto(second)).thenReturn(secondDTO);

        assertEquals(List.of(firstDTO, secondDTO), clientService.findAll());
        verify(clientMapper).toDto(first);
        verify(clientMapper).toDto(second);
    }

    @Test
    void findByIdReturnsMappedClient() {
        Client client = client();
        ClientDTO expected = clientDTO(7L, "Ana", "Torres", "Salas", "PEN", 5000.0);
        when(clientRepository.findById(7L)).thenReturn(Optional.of(client));
        when(clientMapper.toDto(client)).thenReturn(expected);

        assertEquals(expected, clientService.findById(7L));
        verify(clientRepository).findById(7L);
    }

    @Test
    void updateChangesBusinessFieldsAndPreservesIdAndCreationDate() {
        Client existing = client();
        existing.setClientId(7L);
        LocalDateTime creationDate = LocalDateTime.of(2025, 1, 1, 10, 0);
        existing.setCreationDate(creationDate);
        ClientDTO request = clientDTO(null, "Luis", "Ramos", "Diaz", "USD", 3200.0);
        ClientDTO expected = clientDTO(7L, "Luis", "Ramos", "Diaz", "USD", 3200.0);

        when(clientRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(clientRepository.save(existing)).thenReturn(existing);
        when(clientMapper.toDto(existing)).thenReturn(expected);

        assertEquals(expected, clientService.update(7L, request));
        assertEquals(7L, existing.getClientId());
        assertEquals(creationDate, existing.getCreationDate());
        assertEquals("Luis", existing.getFirstName());
        assertEquals("Ramos", existing.getPaternalLastName());
        assertEquals("Diaz", existing.getMaternalLastName());
        assertEquals("USD", existing.getCurrencyOfIncome());
        assertEquals(3200.0, existing.getMonthlyIncome());
        verify(clientRepository).save(existing);
    }

    @Test
    void deleteRemovesExistingClient() {
        Client existing = client();
        when(clientRepository.findById(7L)).thenReturn(Optional.of(existing));

        clientService.delete(7L);

        verify(clientRepository).delete(existing);
    }

    @Test
    void operationsThrowWhenClientDoesNotExist() {
        when(clientRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ClientNotFoundException.class, () -> clientService.findById(7L));
        assertThrows(ClientNotFoundException.class, () -> clientService.update(7L, clientDTO(null, "Luis", "Ramos", "Diaz", "USD", 3200.0)));
        assertThrows(ClientNotFoundException.class, () -> clientService.delete(7L));
        verifyNoInteractions(clientMapper);
    }

    private static Client client() {
        return new Client();
    }

    private static ClientDTO clientDTO(Long id, String firstName, String paternalLastName,
                                       String maternalLastName, String currency, Double income) {
        return ClientDTO.builder()
                .clientId(id)
                .firstName(firstName)
                .paternalLastName(paternalLastName)
                .maternalLastName(maternalLastName)
                .currencyOfIncome(currency)
                .monthlyIncome(income)
                .build();
    }
}
