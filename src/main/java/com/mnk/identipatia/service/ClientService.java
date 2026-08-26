package com.mnk.identipatia.service;

import com.mnk.identipatia.dto.ClientDTO;
import com.mnk.identipatia.exception.ClientNotFoundException;
import com.mnk.identipatia.mapper.ClientMapper;
import com.mnk.identipatia.model.Client;
import com.mnk.identipatia.repository.ClientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

    public ClientService(ClientRepository clientRepository, ClientMapper clientMapper) {
        this.clientRepository = clientRepository;
        this.clientMapper = clientMapper;
    }

    public ClientDTO create(ClientDTO clientDTO) {
        Client client = clientMapper.toEntity(clientDTO);
        client.setClientId(null);
        client.setCreationDate(LocalDateTime.now());
        return clientMapper.toDto(clientRepository.save(client));
    }

    public List<ClientDTO> findAll() {
        return clientRepository.findAll().stream()
                .map(clientMapper::toDto)
                .toList();
    }

    public ClientDTO findById(Long clientId) {
        return clientMapper.toDto(getClient(clientId));
    }

    public ClientDTO update(Long clientId, ClientDTO clientDTO) {
        Client client = getClient(clientId);
        client.setFirstName(clientDTO.getFirstName());
        client.setPaternalLastName(clientDTO.getPaternalLastName());
        client.setMaternalLastName(clientDTO.getMaternalLastName());
        client.setCurrencyOfIncome(clientDTO.getCurrencyOfIncome());
        client.setMonthlyIncome(clientDTO.getMonthlyIncome());
        return clientMapper.toDto(clientRepository.save(client));
    }

    public void delete(Long clientId) {
        Client client = getClient(clientId);
        clientRepository.delete(client);
    }

    private Client getClient(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
    }
}
