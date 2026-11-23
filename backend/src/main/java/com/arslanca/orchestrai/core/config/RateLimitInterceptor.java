package com.arslanca.orchestrai.core.config;

import com.arslanca.orchestrai.business.abstracts.RateLimitService;
import com.arslanca.orchestrai.core.config.projectgeneration.ProjectGenerationFeatureProperties;
import com.arslanca.orchestrai.core.exception.ApiErrorResponseWriter;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final ApiErrorResponseWriter errorWriter;
    private final ProjectGenerationFeatureProperties featureProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitInterceptor(
            final RateLimitService rateLimitService,
            final ApiErrorResponseWriter errorWriter,
            final ProjectGenerationFeatureProperties featureProperties) {
        this.rateLimitService = rateLimitService;
        this.errorWriter = errorWriter;
        this.featureProperties = featureProperties;
    }


    @Override
    public boolean preHandle(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Object handler) throws Exception {
        if (isDisabledLegacyGenerateRequest(request)) {
            return true;
        }

        final var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            try {
                if (!rateLimitService.tryAcquire(user.id())) {
                    errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Rate limit exceeded. Please wait.");
                    return false;
                }
            } catch (Exception e) {
                final String path = request.getRequestURI();
                log.warn("RateLimitService failed for path {}: {}", path, e.getMessage());
                if (isExpensiveMutation(request)) {
                    log.error("Failing closed for critical mutation path: {}", path);
                    errorWriter.write(
                            response,
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "SERVICE_UNAVAILABLE",
                            "Rate limiting is temporarily unavailable for this mutation endpoint. Please retry shortly.");
                    return false;
                }
                log.info("Failing open for non-critical path: {}", path);
            }
        }

        return true;
    }

    private boolean isDisabledLegacyGenerateRequest(final HttpServletRequest request) {
        if (featureProperties.isLegacyGenerateEnabled()) {
            return false;
        }

        final String path = request.getRequestURI();
        return path != null
                && "POST".equalsIgnoreCase(request.getMethod())
                && (pathMatcher.match("/api/v1/projects/generate", path)
                || pathMatcher.match("/api/v1/projects/generate/", path));
    }

    private boolean isExpensiveMutation(final HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        
        return pathMatcher.match("/api/v1/projects/generate", path)
                || pathMatcher.match("/api/v1/projects/generate/", path)
                || pathMatcher.match("/api/v1/project-generation/prepare", path)
                || pathMatcher.match("/api/v1/project-generation/prepare/", path)
                || pathMatcher.match("/api/v1/project-generation/preview", path)
                || pathMatcher.match("/api/v1/project-generation/preview/", path)
                || pathMatcher.match("/api/v1/project-generation/previews/*/accept", path)
                || pathMatcher.match("/api/v1/project-generation/previews/*/accept/", path)
                || pathMatcher.match("/api/v1/project-generation/start", path)
                || pathMatcher.match("/api/v1/project-generation/start/", path)
                || pathMatcher.match("/api/v1/conversations/chats/*/messages", path)
                || pathMatcher.match("/api/v1/conversations/chats/*/messages/", path);
    }
}
