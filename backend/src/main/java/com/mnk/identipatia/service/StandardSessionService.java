package com.mnk.identipatia.service;

import com.mnk.identipatia.config.StandardSessionProperties;
import com.mnk.identipatia.dto.ConsentRequest;
import com.mnk.identipatia.dto.ConsentResponse;
import com.mnk.identipatia.dto.StandardSessionCreateRequest;
import com.mnk.identipatia.dto.StandardSessionResponse;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.exception.StandardSessionRequiredException;
import com.mnk.identipatia.model.ConsentDecision;
import com.mnk.identipatia.model.ConsentEvent;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.StandardSessionCloseReason;
import com.mnk.identipatia.model.StandardSessionStatus;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.ConsentEventRepository;
import com.mnk.identipatia.repository.StandardSessionRepository;
import com.mnk.identipatia.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class StandardSessionService {
    private static final String STANDARD = "STANDARD";
    private static final String ACTIVE = "A";
    private static final String SOURCE = "STANDARD_WEB";

    private final UserRepository userRepository;
    private final StandardSessionRepository sessionRepository;
    private final ConsentEventRepository consentRepository;
    private final StandardSessionTokenService tokenService;
    private final StandardSessionResolver resolver;
    private final StandardSessionProperties properties;
    private final Clock clock;

    public StandardSessionService(UserRepository userRepository,
            StandardSessionRepository sessionRepository,
            ConsentEventRepository consentRepository,
            StandardSessionTokenService tokenService,
            StandardSessionResolver resolver,
            StandardSessionProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.consentRepository = consentRepository;
        this.tokenService = tokenService;
        this.resolver = resolver;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CreatedSession create(StandardSessionCreateRequest request, String presentedToken) {
        validateDocumentType(request.getDoiType());
        User user = userRepository.findByDoi(request.getDoi())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "STANDARD_REGISTRATION_REQUIRED", "A STANDARD registration is required"));
        if (!request.getDoiType().equalsIgnoreCase(user.getDoiType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT", "Document data is invalid");
        }
        if (!STANDARD.equalsIgnoreCase(user.getUserType()) || !ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STANDARD_SESSION_NOT_AVAILABLE",
                    "A STANDARD session is not available for this registration");
        }

        Instant now = clock.instant();
        Optional<StandardSession> previousSession = resolver.resolveValidForRotation(presentedToken);
        previousSession.ifPresent(previous -> {
            previous.setStatus(StandardSessionStatus.CLOSED);
            previous.setClosedAt(now);
            previous.setCloseReason(StandardSessionCloseReason.ROTATED);
            sessionRepository.save(previous);
        });

        String rawToken = tokenService.generateToken();
        Instant absoluteExpiresAt = now.plus(properties.getAbsoluteTimeout());
        Instant expiresAt = now.plus(properties.getInactivityTimeout());
        if (expiresAt.isAfter(absoluteExpiresAt)) {
            expiresAt = absoluteExpiresAt;
        }

        StandardSession session = new StandardSession();
        session.setSessionId(UUID.randomUUID());
        session.setUser(user);
        session.setTokenHash(tokenService.hash(rawToken));
        session.setStatus(StandardSessionStatus.ACTIVE);
        session.setCreatedAt(now);
        session.setLastActivityAt(now);
        session.setExpiresAt(expiresAt);
        session.setAbsoluteExpiresAt(absoluteExpiresAt);
        sessionRepository.save(session);

        return new CreatedSession(rawToken, sessionResponse(session), previousSession.isPresent());
    }

    @Transactional
    public StandardSessionResponse get(String rawToken) {
        return sessionResponse(requireSession(rawToken).session());
    }

    @Transactional
    public ConsentResult decideConsent(String rawToken, ConsentRequest request) {
        StandardSessionContext context = resolver.resolveForConsent(rawToken)
                .orElseThrow(StandardSessionRequiredException::new);
        if (!properties.getConsentCurrentVersion().equals(request.getConsentVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONSENT_VERSION_OUTDATED",
                    "The displayed consent version is no longer current");
        }

        ConsentDecision requestedDecision = parseDecision(request.getDecision());
        Optional<ConsentEvent> previous = consentRepository.findBySessionAndConsentVersion(
                context.session(), properties.getConsentCurrentVersion());
        if (previous.isPresent()) {
            ConsentEvent existing = previous.get();
            if (existing.getDecision() != requestedDecision) {
                throw new ApiException(HttpStatus.CONFLICT, "CONSENT_ALREADY_DECIDED",
                        "Consent was already decided for this session and version");
            }
            return new ConsentResult(toResponse(existing, context.session()),
                    existing.getDecision() == ConsentDecision.REJECTED);
        }

        if (context.session().getStatus() != StandardSessionStatus.ACTIVE) {
            throw new StandardSessionRequiredException();
        }

        Instant now = clock.instant();
        ConsentEvent event = new ConsentEvent();
        event.setConsentEventId(UUID.randomUUID());
        event.setSession(context.session());
        event.setUser(context.user());
        event.setConsentVersion(properties.getConsentCurrentVersion());
        event.setConsentDocumentHash(properties.getConsentDocumentSha256());
        event.setDecision(requestedDecision);
        event.setSource(SOURCE);
        event.setDecidedAt(now);
        consentRepository.save(event);

        boolean clearCookie = requestedDecision == ConsentDecision.REJECTED;
        if (clearCookie) {
            context.session().setStatus(StandardSessionStatus.CLOSED);
            context.session().setClosedAt(now);
            context.session().setCloseReason(StandardSessionCloseReason.CONSENT_REJECTED);
            sessionRepository.save(context.session());
        }
        return new ConsentResult(toResponse(event, context.session()), clearCookie);
    }

    @Transactional
    public void close(String rawToken) {
        StandardSession session = requireSession(rawToken).session();
        session.setStatus(StandardSessionStatus.CLOSED);
        session.setClosedAt(clock.instant());
        session.setCloseReason(StandardSessionCloseReason.USER_REQUEST);
        sessionRepository.save(session);
    }

    private StandardSessionContext requireSession(String rawToken) {
        return resolver.resolveAndRenew(rawToken).orElseThrow(StandardSessionRequiredException::new);
    }

    private StandardSessionResponse sessionResponse(StandardSession session) {
        boolean accepted = consentRepository.existsBySessionAndConsentVersionAndDecision(
                session, properties.getConsentCurrentVersion(), ConsentDecision.ACCEPTED);
        return new StandardSessionResponse(session.getStatus(), session.getExpiresAt(),
                session.getAbsoluteExpiresAt(), !accepted, properties.getConsentCurrentVersion());
    }

    private ConsentResponse toResponse(ConsentEvent event, StandardSession session) {
        return new ConsentResponse(event.getConsentVersion(), event.getDecision(), event.getDecidedAt(),
                session.getStatus(), event.getDecision() != ConsentDecision.ACCEPTED);
    }

    private static ConsentDecision parseDecision(String value) {
        try {
            return ConsentDecision.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONSENT_DECISION",
                    "Consent decision must be ACCEPTED or REJECTED");
        }
    }

    private static void validateDocumentType(String doiType) {
        if (!"DNI".equalsIgnoreCase(doiType) && !"CE".equalsIgnoreCase(doiType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT", "Document data is invalid");
        }
    }

    public record CreatedSession(String rawToken, StandardSessionResponse response, boolean rotated) { }
    public record ConsentResult(ConsentResponse response, boolean clearCookie) { }
}
