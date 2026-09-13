package com.mnk.identipatia.controller;

import com.mnk.identipatia.dto.ConsentRequest;
import com.mnk.identipatia.dto.ConsentResponse;
import com.mnk.identipatia.dto.CsrfTokenResponse;
import com.mnk.identipatia.dto.StandardSessionCreateRequest;
import com.mnk.identipatia.dto.StandardSessionResponse;
import com.mnk.identipatia.service.StandardSessionCookieService;
import com.mnk.identipatia.service.StandardSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@Tag(name = "STANDARD session", description = "Capacidad temporal server-side; no es login, JWT ni verificación de identidad.")
public class StandardSessionController {
    private final StandardSessionService sessionService;
    private final StandardSessionCookieService cookieService;
    private final Clock clock;

    public StandardSessionController(StandardSessionService sessionService,
            StandardSessionCookieService cookieService, Clock clock) {
        this.sessionService = sessionService;
        this.cookieService = cookieService;
        this.clock = clock;
    }

    @GetMapping("/standard-session/csrf")
    @Operation(summary = "Obtener CSRF", description = "Emite XSRF-TOKEN y devuelve el valor para X-XSRF-TOKEN. CSRF no autentica.")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PostMapping("/standard-sessions")
    @Operation(summary = "Crear sesión STANDARD", description = "Requiere XSRF-TOKEN + X-XSRF-TOKEN. Emite IDENTIPAT_STANDARD_SESSION HttpOnly; la respuesta no contiene token, PII ni IDs.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sesión creada"),
            @ApiResponse(responseCode = "400", description = "INVALID_DOCUMENT o CSRF inválido"),
            @ApiResponse(responseCode = "403", description = "CSRF ausente o inválido"),
            @ApiResponse(responseCode = "409", description = "Registro requerido o sesión no disponible")
    })
    public ResponseEntity<StandardSessionResponse> create(@Valid @RequestBody StandardSessionCreateRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        StandardSessionService.CreatedSession created = sessionService.create(body, cookieService.readToken(request));
        if (created.rotated()) {
            cookieService.clear(response);
        }
        cookieService.write(response, created.rawToken(), created.response().absoluteExpiresAt(), clock.instant());
        return ResponseEntity.status(HttpStatus.CREATED).body(created.response());
    }

    @GetMapping("/standard-session")
    @Operation(summary = "Consultar sesión STANDARD", description = "Requiere la cookie HttpOnly y renueva solo el límite de inactividad, nunca el absoluto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión activa sin PII ni IDs"),
            @ApiResponse(responseCode = "401", description = "STANDARD_SESSION_REQUIRED")
    })
    public StandardSessionResponse get(HttpServletRequest request) {
        return sessionService.get(cookieService.readToken(request));
    }

    @PostMapping("/standard-session/consent")
    @Operation(summary = "Registrar consentimiento", description = "Requiere sesión STANDARD activa y CSRF. El hash lo determina el servidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decisión persistida o respuesta idempotente"),
            @ApiResponse(responseCode = "400", description = "INVALID_CONSENT_DECISION"),
            @ApiResponse(responseCode = "401", description = "STANDARD_SESSION_REQUIRED"),
            @ApiResponse(responseCode = "403", description = "CSRF ausente o inválido"),
            @ApiResponse(responseCode = "409", description = "Versión desactualizada o decisión previa distinta")
    })
    public ConsentResponse consent(@Valid @RequestBody ConsentRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        StandardSessionService.ConsentResult result = sessionService.decideConsent(
                cookieService.readToken(request), body);
        if (result.clearCookie()) {
            cookieService.clear(response);
        }
        return result.response();
    }

    @DeleteMapping("/standard-session")
    @Operation(summary = "Cerrar sesión STANDARD", description = "Requiere sesión activa y CSRF; revoca server-side y elimina la cookie.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesión cerrada"),
            @ApiResponse(responseCode = "401", description = "STANDARD_SESSION_REQUIRED"),
            @ApiResponse(responseCode = "403", description = "CSRF ausente o inválido")
    })
    public ResponseEntity<Void> close(HttpServletRequest request, HttpServletResponse response) {
        sessionService.close(cookieService.readToken(request));
        cookieService.clear(response);
        return ResponseEntity.noContent().build();
    }
}
