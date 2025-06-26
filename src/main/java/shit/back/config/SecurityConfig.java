package shit.back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Autowired
    private SecurityProperties securityProperties;

    @Value("${test.payment.enabled:false}")
    private boolean testPaymentEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        log.info("Configuring security filter chain. API Security enabled: {}",
                securityProperties.getApi().isEnabled());

        http
                // Настраиваем CSRF защиту
                .csrf(csrf -> {
                    var matchers = csrf
                            .csrfTokenRepository(
                                    org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                            .ignoringRequestMatchers(
                                    // Telegram Bot API - не нужен CSRF
                                    "/api/bot/**",
                                    "/webhook/telegram", // Исправлен путь для webhook
                                    "/webhook/**", // Разрешаем все webhook пути

                                    // Health checks и monitoring - публичные endpoints
                                    "/actuator/health",
                                    "/actuator/info");

                    // Тестовые endpoints исключаем из CSRF если включен тестовый режим
                    if (testPaymentEnabled) {
                        log.info("🧪 TEST MODE: Исключаем тестовые payment endpoints из CSRF защиты");
                        matchers.ignoringRequestMatchers("/api/payment/callback/test/**");
                    }
                })

                // Настраиваем CORS
                .cors(cors -> {
                    if (securityProperties.getCors().isEnabled()) {
                        cors.configurationSource(corsConfigurationSource());
                    } else {
                        cors.disable();
                    }
                })

                // Stateless сессии
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Настройка авторизации
                .authorizeHttpRequests(authz -> {
                    authz
                            // Публичные эндпоинты
                            .requestMatchers("/api/bot/health").permitAll()
                            .requestMatchers("/actuator/health").permitAll()
                            .requestMatchers("/actuator/info").permitAll()

                            // Telegram webhook - КРИТИЧЕСКИ ВАЖНО для бота!
                            .requestMatchers("/webhook/telegram").permitAll()
                            .requestMatchers("/webhook/**").permitAll()

                            // ИСПРАВЛЕНИЕ: Внутренние админские AJAX эндпоинты (без API ключа)
                            .requestMatchers("/admin/api/dashboard/**").permitAll()
                            .requestMatchers("/admin/api/system-health").permitAll()
                            .requestMatchers("/admin/api/dashboard-data").permitAll()
                            .requestMatchers("/admin/api/recent-activity").permitAll()
                            .requestMatchers("/admin/api/quick-stats").permitAll()
                            .requestMatchers("/admin/api/activity-statistics").permitAll()
                            .requestMatchers("/admin/api/refresh-cache").permitAll()
                            .requestMatchers("/admin/api/activity-stream").permitAll()
                            .requestMatchers("/admin/api/activity-stream-categorized").permitAll()

                            // ИСПРАВЛЕНИЕ: Добавляем недостающие activity endpoints
                            .requestMatchers("/admin/api/category-statistics").permitAll()
                            .requestMatchers("/admin/api/activity-logs-by-category").permitAll()

                            // ИСПРАВЛЕНИЕ SSE: Добавляем SSE endpoints для мониторинга
                            .requestMatchers("/admin/api/metrics/stream").permitAll()
                            .requestMatchers("/admin/api/metrics/current").permitAll()
                            .requestMatchers("/admin/api/metrics/health").permitAll()
                            .requestMatchers("/admin/api/metrics/stats").permitAll()
                            .requestMatchers("/admin/api/metrics/test-connection").permitAll()
                            .requestMatchers("/admin/api/monitoring-fast").permitAll()
                            .requestMatchers("/admin/api/environment-info").permitAll();

                    // Тестовые endpoints только в dev режиме (если включен тестовый режим)
                    if (testPaymentEnabled) {
                        log.info("🧪 TEST MODE: Разрешен доступ к тестовым payment endpoints без API ключа");
                        authz.requestMatchers("/api/payment/callback/test/**").permitAll();
                    }

                    authz
                            // API эндпоинты требуют аутентификации
                            .requestMatchers("/api/**").authenticated()
                            // Остальные admin API эндпоинты требуют аутентификации
                            .requestMatchers("/admin/api/**").authenticated()

                            // Все остальные запросы разрешены
                            .anyRequest().permitAll();
                });

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

        // Разрешенные методы (добавляем OPTIONS для preflight запросов SSE)
        List<String> allowedMethods = new ArrayList<>(securityProperties.getCors().getAllowedMethods());
        if (!allowedMethods.contains("OPTIONS")) {
            allowedMethods.add("OPTIONS");
        }
        configuration.setAllowedMethods(allowedMethods);

        // Разрешенные заголовки (добавляем специальные заголовки для SSE)
        List<String> allowedHeaders = new ArrayList<>(securityProperties.getCors().getAllowedHeaders());
        if (!allowedHeaders.contains("Cache-Control")) {
            allowedHeaders.add("Cache-Control");
        }
        if (!allowedHeaders.contains("Last-Event-ID")) {
            allowedHeaders.add("Last-Event-ID");
        }
        configuration.setAllowedHeaders(allowedHeaders);

        // Разрешаем credentials
        configuration.setAllowCredentials(true);

        // Время кеширования preflight запросов
        configuration.setMaxAge(securityProperties.getCors().getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/admin/api/**", configuration);

        log.info("CORS configuration applied for origins: {} (API and Admin API)", allowedOrigins);
        log.info("🔧 ИСПРАВЛЕНИЕ: Внутренние админские AJAX эндпоинты исключены из API-key аутентификации");

        return source;
    }
}
