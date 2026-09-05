package com.mnk.identipatia.controller;

import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.dto.LoginRequestDTO;
import com.mnk.identipatia.dto.LoginResponseDTO;
import com.mnk.identipatia.service.JwtService;
import com.mnk.identipatia.service.UserService;
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

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public UserController(UserService userService, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequestDTO.getDoi(),
                        loginRequestDTO.getPassword()));

            String accessToken = jwtService.generateToken(loginRequestDTO.getDoi());

            return ResponseEntity.ok(new LoginResponseDTO("Bearer", accessToken));
    }

    @PostMapping
    public ResponseEntity<UserDTO> create(@Valid @RequestBody UserDTO userDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(userDTO));
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> findById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }

    @GetMapping("/doi/{doi}")
    public ResponseEntity<UserDTO> findByDoi(@PathVariable String doi) {
        return ResponseEntity.ok(userService.findByDoi(doi));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserDTO> update(
            @PathVariable Long userId,
            @Valid @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(userService.update(userId, userDTO));
    }

    @PutMapping("/admin/{userId}")
    public ResponseEntity<UserDTO> updateAll(
            @PathVariable Long userId,
            @Valid @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(userService.updateAll(userId, userDTO));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable Long userId) {
        userService.delete(userId);
        return ResponseEntity.noContent().build();
    }
}
