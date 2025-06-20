package shit.back.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import shit.back.exception.core.SecurityException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер контекста безопасности
 * Управляет контекстом безопасности и сессиями пользователей
 * 
 * Реализация Security Patterns (Week 3-4):
 * - Централизованное управление контекстом
 * - Валидация сессий и токенов
 * - Tracking активности пользователей
 * - Security event logging
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring - Security Patterns Implementation
 */
@Slf4j
@Component
public class SecurityContextManager {

    private final Map<String, UserSecurityContext> userContexts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastActivityMap = new ConcurrentHashMap<>();

    /**
     * Получение текущего пользователя из контекста безопасности
     */
    public Optional<String> getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String userId = extractUserIdFromAuthentication(authentication);
                log.debug("🔐 Получен ID текущего пользователя: {}", userId);
                return Optional.ofNullable(userId);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при получении ID текущего пользователя: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Получение текущего пользователя с обязательной авторизацией
     * 
     * @throws SecurityException если пользователь не авторизован
     */
    public String getCurrentUserIdRequired() {
        return getCurrentUserId()
                .orElseThrow(() -> SecurityException.unauthorized("unknown", "GET_CURRENT_USER", "security_context"));
    }

    /**
     * Проверка является ли пользователь администратором
     */
    public boolean isCurrentUserAdmin() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return false;
            }

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") ||
                            auth.getAuthority().equals("ADMIN"));

            if (isAdmin) {
                log.info("🔑 Подтверждены административные права для пользователя: {}",
                        getCurrentUserId().orElse("unknown"));
            }

            return isAdmin;
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при проверке прав администратора: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверка прав администратора с выбросом исключения
     * 
     * @throws SecurityException если пользователь не администратор
     */
    public void requireAdminAccess(String operation) {
        String userId = getCurrentUserIdRequired();

        if (!isCurrentUserAdmin()) {
            log.warn("🚫 Отказ в доступе к административной операции '{}' для пользователя: {}", operation, userId);
            throw SecurityException.insufficientPermissions(userId, operation, "admin_panel");
        }

        log.info("✅ Подтвержден административный доступ к операции '{}' для пользователя: {}", operation, userId);
    }

    /**
     * Создание контекста безопасности для пользователя
     */
    public UserSecurityContext createUserContext(String userId, String sessionId, Map<String, Object> attributes) {
        UserSecurityContext context = UserSecurityContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .attributes(new ConcurrentHashMap<>(attributes))
                .isValid(true)
                .build();

        userContexts.put(userId, context);
        updateLastActivity(userId);

        log.info("🔐 Создан контекст безопасности для пользователя: {} (session: {})", userId, sessionId);
        return context;
    }

    /**
     * Получение контекста безопасности пользователя
     */
    public Optional<UserSecurityContext> getUserContext(String userId) {
        UserSecurityContext context = userContexts.get(userId);
        if (context != null && context.isValid()) {
            updateLastActivity(userId);
            return Optional.of(context);
        }
        return Optional.empty();
    }

    /**
     * Обновление времени последней активности
     */
    public void updateLastActivity(String userId) {
        lastActivityMap.put(userId, LocalDateTime.now());

        UserSecurityContext context = userContexts.get(userId);
        if (context != null) {
            context.setLastActivity(LocalDateTime.now());
        }
    }

    /**
     * Проверка активности пользователя
     */
    public boolean isUserActiveWithinMinutes(String userId, int minutes) {
        LocalDateTime lastActivity = lastActivityMap.get(userId);
        if (lastActivity == null) {
            return false;
        }

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        return lastActivity.isAfter(threshold);
    }

    /**
     * Инвалидация контекста пользователя
     */
    public void invalidateUserContext(String userId, String reason) {
        UserSecurityContext context = userContexts.get(userId);
        if (context != null) {
            context.setValid(false);
            context.setInvalidationReason(reason);
            context.setInvalidatedAt(LocalDateTime.now());

            log.info("🔒 Инвалидирован контекст пользователя: {} по причине: {}", userId, reason);
        }

        lastActivityMap.remove(userId);
    }

    /**
     * Очистка истекших контекстов
     */
    public int cleanupExpiredContexts(int maxInactiveMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(maxInactiveMinutes);
        final int[] cleanedCount = { 0 }; // Используем массив для изменяемого счетчика

        userContexts.entrySet().removeIf(entry -> {
            String userId = entry.getKey();
            UserSecurityContext context = entry.getValue();

            if (context.getLastActivity().isBefore(threshold)) {
                invalidateUserContext(userId, "EXPIRED_INACTIVITY");
                cleanedCount[0]++;
                return true;
            }
            return false;
        });

        if (cleanedCount[0] > 0) {
            log.info("🧹 Очищено {} истекших контекстов безопасности", cleanedCount[0]);
        }

        return cleanedCount[0];
    }

    /**
     * Получение статистики активных контекстов
     */
    public SecurityContextStats getContextStats() {
        int totalContexts = userContexts.size();
        long validContexts = userContexts.values().stream()
                .mapToLong(ctx -> ctx.isValid() ? 1 : 0)
                .sum();

        long activeUsers = lastActivityMap.entrySet().stream()
                .mapToLong(entry -> {
                    LocalDateTime lastActivity = entry.getValue();
                    return lastActivity.isAfter(LocalDateTime.now().minusMinutes(30)) ? 1 : 0;
                })
                .sum();

        return SecurityContextStats.builder()
                .totalContexts(totalContexts)
                .validContexts((int) validContexts)
                .activeUsers((int) activeUsers)
                .lastCleanup(LocalDateTime.now())
                .build();
    }

    // Приватные методы

    private String extractUserIdFromAuthentication(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof String) {
            return (String) principal;
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }

        return authentication.getName();
    }

    // Data Transfer Objects

    @lombok.Data
    @lombok.Builder
    public static class UserSecurityContext {
        private String userId;
        private String sessionId;
        private LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        private LocalDateTime invalidatedAt;
        private boolean isValid;
        private String invalidationReason;
        private Map<String, Object> attributes;
    }

    @lombok.Data
    @lombok.Builder
    public static class SecurityContextStats {
        private int totalContexts;
        private int validContexts;
        private int activeUsers;
        private LocalDateTime lastCleanup;
    }
}