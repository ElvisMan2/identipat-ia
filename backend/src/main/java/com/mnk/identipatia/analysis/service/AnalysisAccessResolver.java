package com.mnk.identipatia.analysis.service;

import com.mnk.identipatia.config.BearerTokenRequest;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.exception.StandardSessionRequiredException;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.StandardSessionContext;
import com.mnk.identipatia.service.StandardSessionCookieService;
import com.mnk.identipatia.service.StandardSessionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AnalysisAccessResolver {
    private static final String ADMIN = "ADMIN";
    private static final String ACTIVE = "A";

    private final UserRepository userRepository;
    private final StandardSessionResolver sessionResolver;
    private final StandardSessionCookieService cookieService;

    public AnalysisAccessResolver(UserRepository userRepository,
            StandardSessionResolver sessionResolver,
            StandardSessionCookieService cookieService) {
        this.userRepository = userRepository;
        this.sessionResolver = sessionResolver;
        this.cookieService = cookieService;
    }

    public AnalysisAccessContext resolve(HttpServletRequest request, Authentication authentication) {
        if (BearerTokenRequest.isPresent(request)) {
            return resolveAdmin(authentication);
        }

        StandardSessionContext context = sessionResolver.resolveAndRenew(cookieService.readToken(request))
                .orElseThrow(StandardSessionRequiredException::new);
        return AnalysisAccessContext.standard(context.user(), context.session());
    }

    private AnalysisAccessContext resolveAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw adminAuthenticationRequired();
        }

        User user = userRepository.findByDoi(authentication.getName())
                .orElseThrow(AnalysisAccessResolver::adminAuthenticationRequired);
        if (!ADMIN.equalsIgnoreCase(user.getUserType()) || !ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw adminAuthenticationRequired();
        }
        return AnalysisAccessContext.admin(user);
    }

    private static ApiException adminAuthenticationRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTHENTICATION_REQUIRED",
                "A valid active ADMIN Bearer token is required");
    }
}
