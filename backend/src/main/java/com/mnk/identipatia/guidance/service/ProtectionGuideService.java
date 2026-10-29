package com.mnk.identipatia.guidance.service;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.guidance.dto.ProtectionGuideCreateRequest;
import com.mnk.identipatia.guidance.dto.ProtectionGuideResponse;
import com.mnk.identipatia.guidance.dto.ProtectionGuideUpdateRequest;
import com.mnk.identipatia.guidance.model.ProtectionGuide;
import com.mnk.identipatia.guidance.model.ProtectionGuideSource;
import com.mnk.identipatia.guidance.model.ProtectionGuideStatus;
import com.mnk.identipatia.guidance.repository.ProtectionGuideRepository;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProtectionGuideService {
    public static final int HTML_MAX_LENGTH = 50_000;
    private static final Pattern FORBIDDEN_DESCRIPTION_CONTENT = Pattern.compile(
            "(?is)<[^>]*>|(?:https?://|www\\.)\\S+");

    private final ProtectionGuideRepository repository;
    private final ProtectionGuideVersionService versionService;
    private final ProtectionGuideSanitizer sanitizer;
    private final ProtectionGuideContentHasher hasher;
    private final UserRepository userRepository;
    private final Clock clock;

    public ProtectionGuideService(ProtectionGuideRepository repository,
            ProtectionGuideVersionService versionService,
            ProtectionGuideSanitizer sanitizer,
            ProtectionGuideContentHasher hasher,
            UserRepository userRepository,
            Clock clock) {
        this.repository = repository;
        this.versionService = versionService;
        this.sanitizer = sanitizer;
        this.hasher = hasher;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProtectionGuideResponse> list() {
        return repository.findAllByOrderByProtectionTypeAscVersionDesc().stream()
                .map(ProtectionGuideService::response).toList();
    }

    @Transactional(readOnly = true)
    public ProtectionGuideResponse active(ProtectionType protectionType) {
        return response(repository.findByProtectionTypeAndStatus(protectionType, ProtectionGuideStatus.ACTIVE)
                .orElseThrow(ProtectionGuideService::activeNotFound));
    }

    @Transactional(readOnly = true)
    public ProtectionGuideResponse get(UUID guideId) {
        return response(require(guideId));
    }

    @Transactional
    public ProtectionGuideResponse create(ProtectionGuideCreateRequest request, Authentication authentication) {
        if (request == null || request.protectionType() == null) {
            throw invalidGuide();
        }
        Content content = validate(request.shortDescription(), request.nextStepsContentHtml());
        User actor = actor(authentication);
        return response(createDraft(request.protectionType(), content, actor, clock.instant()));
    }

    @Transactional
    public ProtectionGuideResponse cloneActive(ProtectionType protectionType, Authentication authentication) {
        User actor = actor(authentication);
        long version = versionService.nextVersion(protectionType);
        ProtectionGuide active = repository
                .findByProtectionTypeAndStatus(protectionType, ProtectionGuideStatus.ACTIVE)
                .orElseThrow(ProtectionGuideService::activeNotFound);
        Content content = validate(active.getShortDescription(), active.getNextStepsContentHtml());
        return response(createDraft(protectionType, version, content, actor, clock.instant()));
    }

    @Transactional
    public ProtectionGuideResponse update(UUID guideId, ProtectionGuideUpdateRequest request,
            Authentication authentication) {
        if (request == null) {
            throw invalidGuide();
        }
        Content content = validate(request.shortDescription(), request.nextStepsContentHtml());
        ProtectionGuide guide = repository.findByIdForUpdate(guideId)
                .orElseThrow(ProtectionGuideService::notFound);
        if (guide.getStatus() != ProtectionGuideStatus.DRAFT) {
            throw immutableGuide();
        }
        User actor = actor(authentication);
        guide.setShortDescription(content.shortDescription());
        guide.setNextStepsContentHtml(content.sanitizedHtml());
        guide.setContentSha256(hasher.sha256(guide.getProtectionType(), content.shortDescription(),
                content.sanitizedHtml()));
        guide.setUpdatedBy(actor.getUserId());
        guide.setUpdatedAt(clock.instant());
        return response(guide);
    }

    @Transactional
    public ProtectionGuideResponse activate(UUID guideId, Authentication authentication) {
        ProtectionGuide target = repository.findById(guideId).orElseThrow(ProtectionGuideService::notFound);
        versionService.lock(target.getProtectionType());
        target = repository.findByIdForUpdate(guideId).orElseThrow(ProtectionGuideService::notFound);
        if (target.getStatus() == ProtectionGuideStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "PROTECTION_GUIDE_ALREADY_ACTIVE",
                    "The protection guide is already active");
        }
        if (target.getStatus() != ProtectionGuideStatus.DRAFT) {
            throw immutableGuide();
        }

        Content content = validate(target.getShortDescription(), target.getNextStepsContentHtml());
        User actor = actor(authentication);
        Instant now = clock.instant();
        ProtectionGuide current = repository.findByProtectionTypeAndStatus(
                target.getProtectionType(), ProtectionGuideStatus.ACTIVE).orElse(null);
        if (current != null) {
            current.setStatus(ProtectionGuideStatus.RETIRED);
            current.setRetiredAt(now);
            repository.saveAndFlush(current);
        }

        target.setShortDescription(content.shortDescription());
        target.setNextStepsContentHtml(content.sanitizedHtml());
        target.setContentSha256(hasher.sha256(target.getProtectionType(), content.shortDescription(),
                content.sanitizedHtml()));
        target.setStatus(ProtectionGuideStatus.ACTIVE);
        target.setActivatedBy(actor.getUserId());
        target.setActivatedAt(now);
        target.setLastVerifiedAt(now);
        return response(target);
    }

    private ProtectionGuide createDraft(ProtectionType protectionType, Content content, User actor, Instant now) {
        return createDraft(protectionType, versionService.nextVersion(protectionType), content, actor, now);
    }

    private ProtectionGuide createDraft(ProtectionType protectionType, long version, Content content,
            User actor, Instant now) {
        ProtectionGuide guide = new ProtectionGuide();
        guide.setGuideId(UUID.randomUUID());
        guide.setProtectionType(protectionType);
        guide.setVersion(version);
        guide.setStatus(ProtectionGuideStatus.DRAFT);
        guide.setShortDescription(content.shortDescription());
        guide.setNextStepsContentHtml(content.sanitizedHtml());
        guide.setContentSha256(hasher.sha256(protectionType, content.shortDescription(), content.sanitizedHtml()));
        guide.setCreatedSource(ProtectionGuideSource.ADMIN);
        guide.setCreatedBy(actor.getUserId());
        guide.setCreatedAt(now);
        return repository.save(guide);
    }

    private Content validate(String rawDescription, String rawHtml) {
        if (rawDescription == null || rawHtml == null || rawHtml.length() > HTML_MAX_LENGTH) {
            throw invalidGuide();
        }
        String description = rawDescription.trim();
        int length = description.codePointCount(0, description.length());
        if (length < 30 || length > 240 || FORBIDDEN_DESCRIPTION_CONTENT.matcher(description).find()) {
            throw invalidGuide();
        }
        String sanitized;
        try {
            sanitized = sanitizer.sanitize(rawHtml);
        } catch (RuntimeException exception) {
            throw invalidGuide();
        }
        if (sanitized.length() > HTML_MAX_LENGTH || !sanitizer.hasVisibleText(sanitized)) {
            throw invalidGuide();
        }
        return new Content(description, sanitized);
    }

    private ProtectionGuide require(UUID guideId) {
        return repository.findById(guideId).orElseThrow(ProtectionGuideService::notFound);
    }

    private User actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw adminAuthenticationRequired();
        }
        return userRepository.findByDoi(authentication.getName())
                .filter(user -> "ADMIN".equalsIgnoreCase(user.getUserType())
                        && "A".equalsIgnoreCase(user.getStatus()))
                .orElseThrow(ProtectionGuideService::adminAuthenticationRequired);
    }

    private static ProtectionGuideResponse response(ProtectionGuide guide) {
        return new ProtectionGuideResponse(guide.getGuideId(), guide.getProtectionType(), guide.getVersion(),
                guide.getStatus(), guide.getShortDescription(), guide.getNextStepsContentHtml(),
                guide.getContentSha256(), guide.getCreatedSource(), guide.getCreatedAt(), guide.getUpdatedAt(),
                guide.getActivatedAt(), guide.getRetiredAt(), guide.getLastVerifiedAt());
    }

    private static ApiException invalidGuide() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PROTECTION_GUIDE",
                "The protection guide content is invalid");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROTECTION_GUIDE_NOT_FOUND",
                "Protection guide was not found");
    }

    private static ApiException activeNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROTECTION_GUIDE_ACTIVE_NOT_FOUND",
                "An active protection guide was not found");
    }

    private static ApiException immutableGuide() {
        return new ApiException(HttpStatus.CONFLICT, "PROTECTION_GUIDE_IMMUTABLE",
                "Only a DRAFT protection guide can be modified");
    }

    private static ApiException adminAuthenticationRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTHENTICATION_REQUIRED",
                "A valid active ADMIN Bearer token is required");
    }

    private record Content(String shortDescription, String sanitizedHtml) {
    }
}
