package shit.back.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import shit.back.config.SecurityConstants;
import shit.back.config.SecurityProperties;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class ApiKeyAuthFilter implements Filter {

    private static final String ROLE_API = "ROLE_API";
    private static final String AUTH_USER = "api-user";
    private static final String ERROR_AUTH = "Authentication error";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CHARSET_UTF8 = "UTF-8";

    // Public endpoints
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/bot/health", "/", "/static/", "/css/", "/js/", "/images/"
    };
    private static final String ACTUATOR_PREFIX = "/actuator/";
    private static final String WEBHOOK_PREFIX = "/webhook/";

    // Security headers
    private static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    private static final String VALUE_NOSNIFF = "nosniff";
    private static final String VALUE_DENY = "DENY";
    private static final String VALUE_XSS = "1; mode=block";
    private static final String VALUE_REFERRER = "strict-origin-when-cross-origin";

    private final SecurityProperties securityProperties;
    private final ApiKeyValidationService apiKeyValidationService;

    public ApiKeyAuthFilter(SecurityProperties securityProperties, ApiKeyValidationService apiKeyValidationService) {
        this.securityProperties = securityProperties;
        this.apiKeyValidationService = apiKeyValidationService;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("ApiKeyAuthFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        final String requestUri = httpRequest.getRequestURI();
        final String method = httpRequest.getMethod();

        log.info("ApiKeyAuthFilter: Processing request: {} {}", method, requestUri);

        if (isPublicEndpoint(requestUri)) {
            log.info("Public endpoint, skipping authentication: {}", requestUri);
            chain.doFilter(request, response);
            return;
        }

        if (!requestUri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        if (!securityProperties.getApi().isEnabled()) {
            log.warn("API security is disabled - allowing request without authentication");
            chain.doFilter(request, response);
            return;
        }

        try {
            String apiKey = httpRequest.getHeader(securityProperties.getApi().getHeaderName());

            if (!StringUtils.hasText(apiKey)) {
                log.warn("Missing API key for request: {} {}", method, requestUri);
                sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                        SecurityConstants.ERROR_INVALID_API_KEY);
                return;
            }

            if (!apiKeyValidationService.isValidApiKey(apiKey)) {
                log.warn("Invalid API key provided for request: {} {}", method, requestUri);
                sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                        SecurityConstants.ERROR_INVALID_API_KEY);
                return;
            }

            Authentication auth = new UsernamePasswordAuthenticationToken(
                    AUTH_USER,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(ROLE_API)));

            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("API key authentication successful for: {} {}", method, requestUri);

            addSecurityHeaders(httpResponse);

        } catch (Exception e) {
            log.error("Error during API key authentication: {}", e.getMessage(), e);
            sendErrorResponse(httpResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ERROR_AUTH);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        log.info("ApiKeyAuthFilter destroyed");
    }

    private boolean isPublicEndpoint(String requestUri) {
        for (String endpoint : PUBLIC_ENDPOINTS) {
            if (endpoint.endsWith("/") && requestUri.startsWith(endpoint)) {
                return true;
            }
            if (endpoint.equals(requestUri)) {
                return true;
            }
        }
        return requestUri.startsWith(ACTUATOR_PREFIX) || requestUri.startsWith(WEBHOOK_PREFIX);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding(CHARSET_UTF8);

        String jsonResponse = String.format(
                "{\"error\": \"%s\", \"status\": %d, \"timestamp\": \"%s\"}",
                message,
                status,
                java.time.Instant.now());

        response.getWriter().write(jsonResponse);
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, VALUE_NOSNIFF);
        response.setHeader(HEADER_X_FRAME_OPTIONS, VALUE_DENY);
        response.setHeader(HEADER_X_XSS_PROTECTION, VALUE_XSS);
        response.setHeader(HEADER_REFERRER_POLICY, VALUE_REFERRER);
    }
}