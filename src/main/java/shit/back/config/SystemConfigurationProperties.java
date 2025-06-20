package shit.back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;

/**
 * Централизованная конфигурация системы
 * Заменяет хардкоды в коде константами из конфигурации
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.system")
@Validated
public class SystemConfigurationProperties {

    @Valid
    private final Timeouts timeouts = new Timeouts();

    @Valid
    private final Limits limits = new Limits();

    @Valid
    private final Urls urls = new Urls();

    @Valid
    private final Cache cache = new Cache();

    @Data
    public static class Timeouts {
        /**
         * Таймаут для платежных операций
         */
        @NotNull
        private Duration paymentTimeout = Duration.ofMinutes(15);

        /**
         * Таймаут для SSE соединений
         */
        @NotNull
        private Duration sseTimeout = Duration.ofMinutes(30);

        /**
         * Таймаут для операций с балансом
         */
        @NotNull
        private Duration balanceOperationTimeout = Duration.ofSeconds(30);

        /**
         * Интервал очистки истекших операций
         */
        @NotNull
        private Duration cleanupInterval = Duration.ofMinutes(5);
    }

    @Data
    public static class Limits {
        /**
         * Максимальное количество одновременных операций с балансом
         */
        @Min(1)
        @Max(100)
        private int maxConcurrentBalanceOperations = 5;

        /**
         * Максимальное количество SSE подключений
         */
        @Min(1)
        @Max(1000)
        private int maxSseConnections = 100;

        /**
         * Максимальное количество заказов в batch операции
         */
        @Min(1)
        @Max(100)
        private int maxBatchOrderSize = 50;

        /**
         * Максимальная длина заметок администратора
         */
        @Min(100)
        @Max(5000)
        private int maxAdminNotesLength = 1000;
    }

    @Data
    public static class Urls {
        /**
         * Базовый URL для webhook'ов
         */
        @NotBlank
        private String webhookBaseUrl = "${WEBHOOK_BASE_URL:http://localhost:8080}";

        /**
         * URL для редиректа после успешной оплаты
         */
        @NotBlank
        private String paymentSuccessUrl = "${PAYMENT_SUCCESS_URL:${app.system.urls.webhook-base-url}/payment/success}";

        /**
         * URL для редиректа после неудачной оплаты
         */
        @NotBlank
        private String paymentFailureUrl = "${PAYMENT_FAILURE_URL:${app.system.urls.webhook-base-url}/payment/failure}";

        /**
         * URL админ панели
         */
        @NotBlank
        private String adminPanelUrl = "${ADMIN_PANEL_URL:${app.system.urls.webhook-base-url}/admin}";
    }

    @Data
    public static class Cache {
        /**
         * TTL для метрик в кеше
         */
        @NotNull
        private Duration metricsCacheTtl = Duration.ofMinutes(5);

        /**
         * TTL для данных dashboard в кеше
         */
        @NotNull
        private Duration dashboardCacheTtl = Duration.ofMinutes(2);

        /**
         * TTL для статистики пользователей
         */
        @NotNull
        private Duration userStatsCacheTtl = Duration.ofMinutes(10);

        /**
         * Максимальный размер кеша
         */
        @Min(10)
        @Max(10000)
        private int maxCacheSize = 1000;
    }
}