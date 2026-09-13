package com.mnk.identipatia.service;

import com.mnk.identipatia.config.StandardSessionProperties;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.StandardSessionCloseReason;
import com.mnk.identipatia.model.StandardSessionStatus;
import com.mnk.identipatia.repository.StandardSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class StandardSessionResolver {
    private final StandardSessionRepository sessionRepository;
    private final StandardSessionTokenService tokenService;
    private final StandardSessionProperties properties;
    private final Clock clock;

    public StandardSessionResolver(StandardSessionRepository sessionRepository,
            StandardSessionTokenService tokenService,
            StandardSessionProperties properties,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StandardSessionContext> resolveAndRenew(String rawToken) {
        Optional<StandardSession> active = resolveActive(rawToken);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        StandardSession session = active.get();
        Instant now = clock.instant();
        session.setLastActivityAt(now);
        Instant renewed = now.plus(properties.getInactivityTimeout());
        session.setExpiresAt(renewed.isBefore(session.getAbsoluteExpiresAt())
                ? renewed : session.getAbsoluteExpiresAt());
        return Optional.of(new StandardSessionContext(session, session.getUser()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StandardSession> resolveValidForRotation(String rawToken) {
        return resolveActive(rawToken);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StandardSessionContext> resolveForConsent(String rawToken) {
        Optional<StandardSession> found = findByToken(rawToken);
        if (found.isPresent()
                && found.get().getStatus() == StandardSessionStatus.CLOSED
                && found.get().getCloseReason() == StandardSessionCloseReason.CONSENT_REJECTED
                && "A".equalsIgnoreCase(found.get().getUser().getStatus())) {
            return Optional.of(new StandardSessionContext(found.get(), found.get().getUser()));
        }
        return resolveAndRenew(rawToken);
    }

    private Optional<StandardSession> resolveActive(String rawToken) {
        Optional<StandardSession> found = findByToken(rawToken);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        StandardSession session = found.get();
        Instant now = clock.instant();
        if (session.getStatus() != StandardSessionStatus.ACTIVE) {
            return Optional.empty();
        }
        if (!now.isBefore(session.getExpiresAt()) || !now.isBefore(session.getAbsoluteExpiresAt())) {
            session.setStatus(StandardSessionStatus.EXPIRED);
            return Optional.empty();
        }
        if (!"A".equalsIgnoreCase(session.getUser().getStatus())) {
            session.setStatus(StandardSessionStatus.CLOSED);
            session.setClosedAt(now);
            session.setCloseReason(StandardSessionCloseReason.USER_INACTIVE);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    private Optional<StandardSession> findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findByTokenHash(tokenService.hash(rawToken));
    }
}
