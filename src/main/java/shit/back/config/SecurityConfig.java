package shit.back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import shit.back.security.ApiKeyAuthFilter;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    
    @Autowired
    private SecurityProperties securityProperties;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        log.info("Configuring security filter chain. API Security enabled: {}", securityProperties.getApi().isEnabled());
        
        http
            // Настраиваем CSRF защиту
            .csrf(csrf -> csrf
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    // Telegram Bot API - не нужен CSRF
                    "/api/bot/**",
                    "/webhook/telegram",  // Исправлен путь для webhook
                    "/webhook/**",        // Разрешаем все webhook пути
                    
                    // Health checks и monitoring - публичные endpoints
                    "/actuator/health",
                    "/actuator/info",
                    
                    // REST API endpoints для внешних интеграций
                    "/api/feature-flags/check/**"
                )
            )
            
            // Настраиваем CORS
            .cors(cors -> {
                if (securityProperties.getCors().isEnabled()) {
                    cors.configurationSource(corsConfigurationSource());
                } else {
                    cors.disable();
                }
            })
            
            // Stateless сессии
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Настройка авторизации
            .authorizeHttpRequests(authz -> authz
                // Публичные эндпоинты
                .requestMatchers("/api/bot/health").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                
                // Telegram webhook - КРИТИЧЕСКИ ВАЖНО для бота!
                .requestMatchers("/webhook/telegram").permitAll()
                .requestMatchers("/webhook/**").permitAll()
                
                // Feature flags endpoints - требуют аутентификации
                .requestMatchers("/api/feature-flags/**").authenticated()
                
                // API эндпоинты требуют аутентификации
                .requestMatchers("/api/**").authenticated()
                
                // Все остальные запросы разрешены
                .anyRequest().permitAll()
            );
        
        // Добавляем фильтр API ключей если включен
        if (securityProperties.getApi().isEnabled()) {
            http.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
            log.info("API Key authentication filter enabled");
        }
        
        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Разрешенные origins
        List<String> allowedOrigins = securityProperties.getCors().getAllowedOrigins();
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            configuration.setAllowedOrigins(allowedOrigins);
        } else {
            configuration.addAllowedOrigin("*");
        }
        
        // Разрешенные методы
        configuration.setAllowedMethods(securityProperties.getCors().getAllowedMethods());
        
        // Разрешенные заголовки
        configuration.setAllowedHeaders(securityProperties.getCors().getAllowedHeaders());
        
        // Разрешаем credentials
        configuration.setAllowCredentials(true);
        
        // Время кеширования preflight запросов
        configuration.setMaxAge(securityProperties.getCors().getMaxAge());
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        log.info("CORS configuration applied for origins: {}", allowedOrigins);
        
        return source;
    }
}
