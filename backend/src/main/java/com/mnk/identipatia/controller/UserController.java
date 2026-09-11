package com.mnk.identipatia.controller;

import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.dto.DocumentRecognitionRequest;
import com.mnk.identipatia.dto.DocumentRecognitionResponse;
import com.mnk.identipatia.dto.LoginRequestDTO;
import com.mnk.identipatia.dto.LoginResponseDTO;
import com.mnk.identipatia.dto.StandardUserRegistrationRequest;
import com.mnk.identipatia.dto.StandardUserRegistrationResponse;
import com.mnk.identipatia.service.JwtService;
import com.mnk.identipatia.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.Map;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Reconocimiento y registro STANDARD, además de la administración de usuarios.")
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
    @Operation(summary = "Iniciar sesión ADMIN", description = "Autentica únicamente a un ADMIN activo y devuelve un JWT Bearer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación administrativa correcta"),
            @ApiResponse(responseCode = "400", description = "Request inválido", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas, usuario STANDARD o ADMIN inactivo")
    })
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequestDTO.getDoi(),
                        loginRequestDTO.getPassword()));

            String accessToken = jwtService.generateToken(loginRequestDTO.getDoi());

            return ResponseEntity.ok(new LoginResponseDTO("Bearer", accessToken));
    }

    @PostMapping("/identify")
    @Operation(summary = "Reconocer documento STANDARD", description = "Indica solo si existe un registro para el DOI y tipo de documento proporcionados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de registro", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DocumentRecognitionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request inválido", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    public ResponseEntity<DocumentRecognitionResponse> identify(
            @Valid @RequestBody DocumentRecognitionRequest request) {
        return ResponseEntity.ok(userService.identifyDocument(request));
    }

    @PostMapping
    @Operation(summary = "Registrar usuario STANDARD", description = "Crea un usuario STANDARD activo; el tipo, estado y contraseña se determinan exclusivamente en el servidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registro STANDARD creado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = StandardUserRegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o DOI duplicado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    public ResponseEntity<StandardUserRegistrationResponse> registerStandard(
            @Valid @RequestBody StandardUserRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.registerStandard(request));
    }

    @GetMapping
    @Operation(summary = "Listar usuarios")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuarios", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = UserDTO.class)))),
            @ApiResponse(responseCode = "401", description = "JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN")
    })
    public ResponseEntity<List<UserDTO>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Consultar usuario por ID")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    public ResponseEntity<UserDTO> findById(@Parameter(description = "ID interno del usuario") @PathVariable Long userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }

    @GetMapping("/doi/{doi}")
    @Operation(summary = "Consultar usuario por DOI")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    public ResponseEntity<UserDTO> findByDoi(@Parameter(description = "Documento de identidad del usuario") @PathVariable String doi) {
        return ResponseEntity.ok(userService.findByDoi(doi));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Actualizar datos de usuario")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    public ResponseEntity<UserDTO> update(
            @Parameter(description = "ID interno del usuario") @PathVariable Long userId,
            @Valid @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(userService.update(userId, userDTO));
    }

    @PutMapping("/admin/{userId}")
    @Operation(summary = "Actualizar integralmente un usuario", description = "Operación administrativa que incluye DOI, tipo, estado, rol y contraseña.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o DOI duplicado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    public ResponseEntity<UserDTO> updateAll(
            @Parameter(description = "ID interno del usuario") @PathVariable Long userId,
            @Valid @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(userService.updateAll(userId, userDTO));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Eliminar usuario")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuario eliminado"),
            @ApiResponse(responseCode = "401", description = "JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(description = "ID interno del usuario") @PathVariable Long userId) {
        userService.delete(userId);
        return ResponseEntity.noContent().build();
    }
}
