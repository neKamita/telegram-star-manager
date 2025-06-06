package shit.back.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import shit.back.config.SecurityConstants;
import shit.back.config.SecurityProperties;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    
    @Autowired
    private SecurityProperties securityProperties;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        
        // Логируем запрос
        log.debug("Processing request: {} {}", method, requestUri);
        
        // Пропускаем публичные эндпоинты
        if (isPublicEndpoint(requestUri)) {
            log.debug("Public endpoint, skipping authentication: {}", requestUri);
            filterChain.doFilter(request, response);
            return;
        }
        
        // Проверяем только API эндпоинты
        if (!requestUri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Если API безопасность отключена
        if (!securityProperties.getApi().isEnabled()) {
            log.warn("API security is disabled - allowing request without authentication");
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            // Получаем API ключ из заголовка
            String apiKey = request.getHeader(securityProperties.getApi().getHeaderName());
            
            if (!StringUtils.hasText(apiKey)) {
                log.warn("Missing API key for request: {} {}", method, requestUri);
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, 
                    SecurityConstants.ERROR_INVALID_API_KEY);
                return;
            }
            
            // Проверяем API ключ
            if (!isValidApiKey(apiKey)) {
                log.warn("Invalid API key provided for request: {} {}", method, requestUri);
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, 
                    SecurityConstants.ERROR_INVALID_API_KEY);
                return;
            }
            
            // Устанавливаем аутентификацию
            Authentication auth = new UsernamePasswordAuthenticationToken(
                "api-user", 
                null, 
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_API"))
            );
            
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            log.debug("API key authentication successful for: {} {}", method, requestUri);
            
            // Добавляем заголовки безопасности
            addSecurityHeaders(response);
            
        } catch (Exception e) {
            log.error("Error during API key authentication: {}", e.getMessage(), e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Authentication error");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String requestUri) {
        return requestUri.equals("/api/bot/health") ||
               requestUri.startsWith("/actuator/") ||
               requestUri.startsWith("/telegram/webhook") ||
               requestUri.equals("/") ||
               requestUri.startsWith("/static/") ||
               requestUri.startsWith("/css/") ||
               requestUri.startsWith("/js/") ||
               requestUri.startsWith("/images/");
    }
    
    private boolean isValidApiKey(String apiKey) {
        // В продакшене здесь должна быть более сложная логика
        // например проверка в базе данных или JWT токен
        return securityProperties.getApi().getKey().equals(apiKey);
    }
    
    private void sendErrorResponse(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String jsonResponse = String.format(
            "{\"error\": \"%s\", \"status\": %d, \"timestamp\": \"%s\"}", 
            message, 
            status, 
            java.time.Instant.now()
        );
        
        response.getWriter().write(jsonResponse);
    }
    
    private void addSecurityHeaders(HttpServletResponse response) {
        // Добавляем стандартные заголовки безопасности
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    }
}
