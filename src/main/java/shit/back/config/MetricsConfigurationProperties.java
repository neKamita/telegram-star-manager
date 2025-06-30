package shit.back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;

/**
 * Конфигурация системы метрик
 * Централизует все настройки сбора и обработки метрик
 */
@Data
@ConfigurationProperties(prefix = "app.metrics")
@Validated
public class MetricsConfigurationProperties {

    @Valid
    private final Collection collection = new Collection();

    @Valid
    private final Performance performance = new Performance();

    @Valid
    private final Sse sse = new Sse();

    @Valid
    private final Fallback fallback = new Fallback();

    @Data
    public static class Collection {
        /**
         * Интервал сбора метрик для background service
         */
        @NotNull
        private Duration interval = Duration.ofMinutes(2);

        /**
         * Интервал обновления кеша метрик
         */
        @NotNull
        private Duration cacheRefreshInterval = Duration.ofSeconds(30);

        /**
         * Включить оптимизированный сбор (только кешированные данные)
         */
        private boolean optimizedModeEnabled = true;

        /**
         * Включить детальное логирование процесса сбора
         */
        private boolean detailedLoggingEnabled = false;

        /**
         * Максимальное время выполнения сбора метрик
         */
        @NotNull
        private Duration maxExecutionTime = Duration.ofSeconds(10);
    }

    @Data
    public static class Performance {
        /**
         * Базовое время отклика для расчетов (мс)
         */
        @Min(10)
        @Max(1000)
        private int baseResponseTimeMs = 50;

        /**
         * Максимальное отклонение времени отклика (мс)
         */
        @Min(5)
        @Max(500)
        private int responseTimeVarianceMs = 30;

        /**
         * Базовое использование памяти (%)
         */
        @Min(20)
        @Max(90)
        private int baseMemoryUsagePercent = 60;

        /**
         * Максимальное отклонение использования памяти (%)
         */
        @Min(5)
        @Max(30)
        private int memoryUsageVariancePercent = 20;

        /**
         * Минимальный коэффициент попаданий в кеш (%)
         */
        @Min(50)
        @Max(100)
        private int minCacheHitRatioPercent = 85;
    }

    @Data
    public static class Sse {
        /**
         * Таймаут для SSE соединений
         */
        @NotNull
        private Duration connectionTimeout = Duration.ofMinutes(30);

        /**
         * Максимальное количество активных SSE соединений
         */
        @Min(1)
        @Max(1000)
        private int maxActiveConnections = 100;

        /**
         * Интервал отправки heartbeat сообщений
         */
        @NotNull
        private Duration heartbeatInterval = Duration.ofSeconds(30);

        /**
         * Включить автоматическую очистку dead connections
         */
        private boolean autoCleanupEnabled = true;
    }

    @Data
    public static class Fallback {
        /**
         * Базовый health score при ошибках
         */
        @Min(0)
        @Max(100)
        private int baseHealthScore = 75;

        /**
         * Использовать последние успешные метрики при ошибках
         */
        private boolean useLastSuccessfulMetrics = true;

        /**
         * Максимальный возраст последних метрик для fallback (минуты)
         */
        @Min(1)
        @Max(60)
        private int maxLastMetricsAgeMinutes = 5;

        /**
         * Включить подробное логирование fallback операций
         */
        private boolean detailedFallbackLogging = true;
    }
}