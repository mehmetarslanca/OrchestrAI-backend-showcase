package com.arslanca.orchestrai.core.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.arslanca.orchestrai.entity.enums.UserRole;

/**
 * JWT Authentication Filter — reads user info from JWT claims.
 *
 * <p>
 * No DB query per request. Deleted users' tokens remain valid until expiry
 * (acceptable trade-off for stateless MVP; revocation list can be added later).
 * </p>
 *
 * <p>
 * All claims extraction is wrapped in a try-catch to prevent unhandled
 * exceptions (NPE from null claims, IllegalArgumentException from invalid
 * UUID/enum values) from bypassing {@code @ControllerAdvice} — since this
 * runs inside a servlet filter, not a controller.
 * </p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }


    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {

        final String bearerToken = extractBearerToken(request);

        if (bearerToken != null && jwtTokenProvider.validateToken(bearerToken)) {
            try {
                final Claims claims = jwtTokenProvider.extractAllClaims(bearerToken);

                final UUID userId = UUID.fromString(claims.getSubject());
                final String username = claims.get("username", String.class);
                final String roleName = claims.get("role", String.class);
                final String sessionIdClaim = claims.get("sid", String.class);
                final UserRole role = UserRole.valueOf(roleName);
                final UUID sessionId = sessionIdClaim == null || sessionIdClaim.isBlank()
                        ? null
                        : UUID.fromString(sessionIdClaim);

                final var authenticatedUser = new AuthenticatedUser(userId, username, role, sessionId);

                final var authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + roleName));
                final var authentication = new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (final Exception ex) {
                LOGGER.warn("JWT claims extraction failed - treating as unauthenticated: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(final HttpServletRequest request) {
        final String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
