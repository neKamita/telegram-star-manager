package shit.back.application.service.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис для управления SSE соединениями и broadcasting метрик
 * Извлечен из BackgroundMetricsService для соблюдения SRP
 * Отвечает только за SSE connections и отправку данных клиентам
 */
@Service
public class MetricsSSEBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(MetricsSSEBroadcastService.class);

    @Autowired
    private MetricsDataFormatterService dataFormatterService;

    // SSE connections management
    private final Set<SseEmitter> activeConnections = ConcurrentHashMap.newKeySet();
    private final AtomicLong totalBroadcasts = new AtomicLong(0);
    private volatile LocalDateTime lastBroadcast;

    /**
     * Добавление нового SSE подключения
     */
    public void addSSEConnection(SseEmitter emitter) {
        activeConnections.add(emitter);
        log.info("➕ Новое SSE подключение добавлено. Всего активных: {}", activeConnections.size());

        // Настройка callbacks для cleanup
        setupEmitterCallbacks(emitter);

        log.info("🔍 SSE подключение зарегистрировано успешно");
    }

    /**
     * Broadcast метрик всем подключенным SSE клиентам
     */
    public void broadcastMetrics(Object metricsData) {
        if (activeConnections.isEmpty()) {
            log.info("📡 Нет активных SSE подключений, пропускаем broadcast");
            return;
        }

        try {
            String eventData = dataFormatterService.formatMetricsAsJson(metricsData);
            int successfulBroadcasts = performBroadcast(eventData);

            totalBroadcasts.incrementAndGet();
            lastBroadcast = LocalDateTime.now();

            log.info("📡 Успешно отправлено {} SSE клиентам", successfulBroadcasts);

        } catch (Exception e) {
            log.error("❌ Ошибка при broadcast метрик: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправка fallback метрик при ошибках
     */
    public void broadcastFallbackMetrics(Object fallbackMetrics) {
        log.warn("🔄 Отправка fallback метрик из-за ошибки");

        try {
            String eventData = dataFormatterService.formatMetricsAsJson(fallbackMetrics);
            performBroadcast(eventData);
        } catch (Exception e) {
            log.error("❌ Ошибка при отправке fallback метрик: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправка начальных метрик новому клиенту
     */
    public void sendInitialMetrics(SseEmitter emitter, Object initialMetrics) {
        if (initialMetrics == null) {
            log.debug("Нет начальных метрик для отправки новому клиенту");
            return;
        }

        try {
            String eventData = dataFormatterService.formatMetricsAsJson(initialMetrics);
            emitter.send(SseEmitter.event()
                    .name("performance-metrics")
                    .data(eventData));
            log.debug("📤 Отправлены начальные метрики новому SSE клиенту");
        } catch (IOException e) {
            log.warn("Не удалось отправить начальные метрики новому SSE клиенту: {}", e.getMessage());
            activeConnections.remove(emitter);
        }
    }

    /**
     * Получение статистики SSE сервиса
     */
    public SSEStatistics getSSEStatistics() {
        return SSEStatistics.builder()
                .activeConnections(activeConnections.size())
                .totalBroadcasts(totalBroadcasts.get())
                .lastBroadcast(lastBroadcast)
                .isHealthy(activeConnections.size() > 0 || lastBroadcast == null ||
                        lastBroadcast.isAfter(LocalDateTime.now().minusMinutes(5)))
                .build();
    }

    /**
     * Принудительное закрытие всех SSE соединений
     */
    public void closeAllConnections() {
        log.info("🔒 Закрытие всех SSE соединений ({})", activeConnections.size());

        activeConnections.forEach(emitter -> {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("Ошибка при закрытии SSE соединения: {}", e.getMessage());
            }
        });

        activeConnections.clear();
        log.info("✅ Все SSE соединения закрыты");
    }

    /**
     * Проверка здоровья SSE сервиса
     */
    public boolean isHealthy() {
        // Сервис здоров если есть активные подключения или недавно были broadcasts
        return activeConnections.size() > 0 ||
                lastBroadcast == null ||
                lastBroadcast.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    /**
     * Очистка мертвых соединений
     */
    public int cleanupDeadConnections() {
        int initialSize = activeConnections.size();

        // Отправляем ping для проверки живых соединений
        activeConnections.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("ping")
                        .data("ping"));
                return false; // Соединение живое, оставляем
            } catch (Exception e) {
                log.debug("Удаление мертвого SSE соединения: {}", e.getMessage());
                return true; // Соединение мертвое, удаляем
            }
        });

        int cleanedUp = initialSize - activeConnections.size();
        if (cleanedUp > 0) {
            log.info("🧹 Очищено {} мертвых SSE соединений", cleanedUp);
        }

        return cleanedUp;
    }

    // ==================== PRIVATE МЕТОДЫ ====================

    /**
     * Выполнение broadcast с удалением мертвых соединений
     */
    private int performBroadcast(String eventData) {
        int successfulBroadcasts = 0;

        log.debug("📡 Отправка {} активным соединениям", activeConnections.size());

        // Удаляем dead connections и отправляем данные живым
        activeConnections.removeIf(emitter -> {
            try {
                log.debug("📤 Отправка SSE event 'performance-metrics'");

                emitter.send(SseEmitter.event()
                        .name("performance-metrics")
                        .data(eventData));

                log.debug("✅ Успешно отправлены данные SSE клиенту");
                return false; // Оставляем в множестве
            } catch (IOException e) {
                log.warn("❌ Удаление мертвого SSE соединения: {}", e.getMessage());
                return true; // Удаляем из множества
            } catch (Exception e) {
                log.error("❌ Критическая ошибка отправки SSE данных: {}", e.getMessage(), e);
                return true; // Удаляем из множества при критических ошибках
            }
        });

        successfulBroadcasts = activeConnections.size();
        return successfulBroadcasts;
    }

    /**
     * Настройка callbacks для SSE emitter
     */
    private void setupEmitterCallbacks(SseEmitter emitter) {
        emitter.onCompletion(() -> {
            activeConnections.remove(emitter);
            log.debug("✅ SSE соединение завершено. Остается: {}", activeConnections.size());
        });

        emitter.onTimeout(() -> {
            activeConnections.remove(emitter);
            log.debug("⏰ SSE соединение истекло по таймауту. Остается: {}", activeConnections.size());
        });

        emitter.onError((ex) -> {
            activeConnections.remove(emitter);
            log.debug("❌ Ошибка SSE соединения: {}. Остается: {}",
                    ex.getMessage(), activeConnections.size());
        });
    }

    // ==================== DATA CLASSES ====================

    /**
     * Статистика SSE сервиса
     */
    public static class SSEStatistics {
        private final Integer activeConnections;
        private final Long totalBroadcasts;
        private final LocalDateTime lastBroadcast;
        private final Boolean isHealthy;

        public static SSEStatisticsBuilder builder() {
            return new SSEStatisticsBuilder();
        }

        private SSEStatistics(SSEStatisticsBuilder builder) {
            this.activeConnections = builder.activeConnections;
            this.totalBroadcasts = builder.totalBroadcasts;
            this.lastBroadcast = builder.lastBroadcast;
            this.isHealthy = builder.isHealthy;
        }

        // Getters
        public Integer getActiveConnections() {
            return activeConnections;
        }

        public Long getTotalBroadcasts() {
            return totalBroadcasts;
        }

        public LocalDateTime getLastBroadcast() {
            return lastBroadcast;
        }

        public Boolean getIsHealthy() {
            return isHealthy;
        }

        public static class SSEStatisticsBuilder {
            private Integer activeConnections;
            private Long totalBroadcasts;
            private LocalDateTime lastBroadcast;
            private Boolean isHealthy;

            public SSEStatisticsBuilder activeConnections(Integer activeConnections) {
                this.activeConnections = activeConnections;
                return this;
            }

            public SSEStatisticsBuilder totalBroadcasts(Long totalBroadcasts) {
                this.totalBroadcasts = totalBroadcasts;
                return this;
            }

            public SSEStatisticsBuilder lastBroadcast(LocalDateTime lastBroadcast) {
                this.lastBroadcast = lastBroadcast;
                return this;
            }

            public SSEStatisticsBuilder isHealthy(Boolean isHealthy) {
                this.isHealthy = isHealthy;
                return this;
            }

            public SSEStatistics build() {
                return new SSEStatistics(this);
            }
        }
    }
}