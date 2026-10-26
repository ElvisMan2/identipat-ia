package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.reporting.dto.DisclaimerUpsertRequest;
import com.mnk.identipatia.reporting.dto.ReportDisclaimerResponse;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import com.mnk.identipatia.reporting.model.ReportDisclaimerSource;
import com.mnk.identipatia.reporting.model.ReportDisclaimerStatus;
import com.mnk.identipatia.reporting.repository.ReportDisclaimerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ReportDisclaimerService {
    public static final int TITLE_MAX_LENGTH = 120;
    public static final int CONTENT_MAX_LENGTH = 5000;

    private final ReportDisclaimerRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ReportDisclaimerService(ReportDisclaimerRepository repository, UserRepository userRepository, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReportDisclaimerResponse> list() {
        return repository.findAllByOrderByVersionDesc().stream().map(ReportDisclaimerService::response).toList();
    }

    @Transactional(readOnly = true)
    public ReportDisclaimerResponse active() {
        return response(repository.findByStatus(ReportDisclaimerStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Exactly one ACTIVE report disclaimer is required")));
    }

    @Transactional(readOnly = true)
    public ReportDisclaimerResponse get(UUID id) {
        return response(require(id));
    }

    @Transactional
    public ReportDisclaimerResponse create(DisclaimerUpsertRequest request, Authentication authentication) {
        Content content = validate(request);
        User actor = actor(authentication);
        Instant now = clock.instant();
        ReportDisclaimer disclaimer = new ReportDisclaimer();
        disclaimer.setDisclaimerId(UUID.randomUUID());
        disclaimer.setVersion(repository.nextVersion());
        disclaimer.setTitle(content.title());
        disclaimer.setContent(content.content());
        disclaimer.setContentSha256(sha256(content.content()));
        disclaimer.setStatus(ReportDisclaimerStatus.DRAFT);
        disclaimer.setCreatedSource(ReportDisclaimerSource.ADMIN);
        disclaimer.setCreatedBy(actor.getUserId());
        disclaimer.setCreatedAt(now);
        return response(repository.save(disclaimer));
    }

    @Transactional
    public ReportDisclaimerResponse cloneActive(Authentication authentication) {
        ReportDisclaimer active = lockedActive()
                .orElseThrow(() -> new IllegalStateException("Exactly one ACTIVE report disclaimer is required"));
        return create(new DisclaimerUpsertRequest(active.getTitle(), active.getContent()), authentication);
    }

    @Transactional
    public ReportDisclaimerResponse update(UUID id, DisclaimerUpsertRequest request, Authentication authentication) {
        Content content = validate(request);
        ReportDisclaimer disclaimer = repository.findByIdForUpdate(id).orElseThrow(ReportDisclaimerService::notFound);
        if (disclaimer.getStatus() != ReportDisclaimerStatus.DRAFT) {
            throw immutableDisclaimer();
        }
        User actor = actor(authentication);
        disclaimer.setTitle(content.title());
        disclaimer.setContent(content.content());
        disclaimer.setContentSha256(sha256(content.content()));
        disclaimer.setUpdatedBy(actor.getUserId());
        disclaimer.setUpdatedAt(clock.instant());
        return response(disclaimer);
    }

    @Transactional
    public ReportDisclaimerResponse activate(UUID id, Authentication authentication) {
        List<ReportDisclaimer> locked = repository.lockAllInStableOrder();
        ReportDisclaimer current = locked.stream()
                .filter(value -> value.getStatus() == ReportDisclaimerStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exactly one ACTIVE report disclaimer is required"));
        if (current.getDisclaimerId().equals(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCLAIMER_ALREADY_ACTIVE",
                    "The report disclaimer is already active");
        }
        ReportDisclaimer target = locked.stream().filter(value -> value.getDisclaimerId().equals(id))
                .findFirst().orElseThrow(ReportDisclaimerService::notFound);
        if (target.getStatus() != ReportDisclaimerStatus.DRAFT) {
            throw immutableDisclaimer();
        }
        User actor = actor(authentication);
        Instant now = clock.instant();
        current.setStatus(ReportDisclaimerStatus.RETIRED);
        current.setRetiredAt(now);
        // Flush the retirement before promoting the target so the partial unique
        // index is never violated by Hibernate's otherwise unspecified update order.
        repository.saveAndFlush(current);
        target.setStatus(ReportDisclaimerStatus.ACTIVE);
        target.setActivatedBy(actor.getUserId());
        target.setActivatedAt(now);
        return response(target);
    }

    private ReportDisclaimer require(UUID id) {
        return repository.findById(id).orElseThrow(ReportDisclaimerService::notFound);
    }

    private java.util.Optional<ReportDisclaimer> lockedActive() {
        return repository.lockAllInStableOrder().stream()
                .filter(value -> value.getStatus() == ReportDisclaimerStatus.ACTIVE)
                .findFirst();
    }

    private User actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTHENTICATION_REQUIRED",
                    "A valid active ADMIN Bearer token is required");
        }
        return userRepository.findByDoi(authentication.getName())
                .filter(user -> "ADMIN".equalsIgnoreCase(user.getUserType()) && "A".equalsIgnoreCase(user.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTHENTICATION_REQUIRED",
                        "A valid active ADMIN Bearer token is required"));
    }

    private static Content validate(DisclaimerUpsertRequest request) {
        if (request == null || request.title() == null || request.content() == null) {
            throw invalidDisclaimer();
        }
        String title = request.title().trim();
        String content = request.content().trim();
        if (title.isEmpty() || content.isEmpty()
                || title.codePointCount(0, title.length()) > TITLE_MAX_LENGTH
                || content.codePointCount(0, content.length()) > CONTENT_MAX_LENGTH) {
            throw invalidDisclaimer();
        }
        return new Content(title, content);
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ReportDisclaimerResponse response(ReportDisclaimer disclaimer) {
        return new ReportDisclaimerResponse(disclaimer.getDisclaimerId(), disclaimer.getVersion(),
                disclaimer.getTitle(), disclaimer.getContent(), disclaimer.getStatus(), disclaimer.getCreatedSource(),
                disclaimer.getCreatedAt(), disclaimer.getUpdatedAt(), disclaimer.getActivatedAt(),
                disclaimer.getRetiredAt());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DISCLAIMER_NOT_FOUND", "Report disclaimer was not found");
    }

    private static ApiException immutableDisclaimer() {
        return new ApiException(HttpStatus.CONFLICT, "DISCLAIMER_IMMUTABLE",
                "Only a DRAFT report disclaimer can be changed");
    }

    private static ApiException invalidDisclaimer() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DISCLAIMER",
                "Title must contain 1 to 120 characters and content 1 to 5000 characters");
    }

    private record Content(String title, String content) {
    }
}
