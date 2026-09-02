package com.mnk.identipatia.controller;

import com.mnk.identipatia.dto.ClientDTO;
import com.mnk.identipatia.dto.LoginRequestDTO;
import com.mnk.identipatia.dto.LoginResponseDTO;
import com.mnk.identipatia.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientService clientService;
    private final AuthenticationManager authenticationManager;

    public ClientController(ClientService clientService, AuthenticationManager authenticationManager) {
        this.clientService = clientService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequestDTO.getUsername(),
                        loginRequestDTO.getPassword()));

        String tokenPayload = loginRequestDTO.getUsername() + ":" + loginRequestDTO.getPassword();
        String accessToken = Base64.getEncoder()
                .encodeToString(tokenPayload.getBytes(StandardCharsets.UTF_8));

        return ResponseEntity.ok(new LoginResponseDTO("Basic", accessToken));
    }

    @PostMapping
    public ResponseEntity<ClientDTO> create(@Valid @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(clientDTO));
    }

    @GetMapping
    public ResponseEntity<List<ClientDTO>> findAll() {
        return ResponseEntity.ok(clientService.findAll());
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientDTO> findById(@PathVariable Long clientId) {
        return ResponseEntity.ok(clientService.findById(clientId));
    }

    @PutMapping("/{clientId}")
    public ResponseEntity<ClientDTO> update(
            @PathVariable Long clientId,
            @Valid @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.update(clientId, clientDTO));
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<Void> delete(@PathVariable Long clientId) {
        clientService.delete(clientId);
        return ResponseEntity.noContent().build();
    }
}
