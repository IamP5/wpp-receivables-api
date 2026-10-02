package com.tubadev.receivables.infrastructure.configuration.security;

import com.tubadev.receivables.infrastructure.configuration.properties.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * Minimal protection of the internal API (campaigns, conversations) with a shared key.
 * The webhook is authenticated by its signature instead. Replace by OAuth2/JWT when exposed beyond the PoC.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Key";
    private static final List<String> PUBLIC_PATHS = List.of("/webhooks/", "/actuator/", "/swagger-ui", "/v3/api-docs");

    private final SecurityProperties properties;

    public ApiKeyFilter(final SecurityProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final var path = request.getRequestURI().substring(request.getContextPath().length());
        return !properties.enabled() || PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws ServletException, IOException {
        final var received = request.getHeader(HEADER);
        if (received != null && MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8), properties.apiKey().getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"missing or invalid X-Api-Key\"}");
    }
}
