package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import shit.back.service.monitoring.ConnectionPoolDiagnosticsService;
import shit.back.service.monitoring.ConnectionPoolHealthService;
import shit.back.service.monitoring.ConnectionPoolLoggingService;

import java.util.Map;

/**
 * Основной сервис для мониторинга состояния connection pools
 * РЕФАКТОРИНГ: Разделен на специализированные сервисы следуя SRP
 * 
 * Делегирует к:
 * - ConnectionPoolHealthService - health checks и базовая статистика
 * - ConnectionPoolDiagnosticsService - детальная диагностика и метрики
 * - ConnectionPoolLoggingService - периодическое логирование
 * 
 * Сохраняет обратную совместимость для существующих клиентов
 */
@Slf4j
@Service
public class ConnectionPoolMonitoringService implements HealthIndicator {

    @Autowired
    private ConnectionPoolHealthService healthService;

    @Autowired
    private ConnectionPoolDiagnosticsService diagnosticsService;

    @Autowired
    private ConnectionPoolLoggingService loggingService;

    // ==================== ДЕЛЕГАЦИЯ К HEALTH SERVICE ====================

    /**
     * Health check для connection pools
     */
    @Override
    public Health health() {
        return healthService.health();
    }

    /**
     * Получение текущей статистики connection pools
     */
    public Map<String, Object> getConnectionPoolStats() {
        log.info("🔍 ДЕЛЕГАЦИЯ: Получение статистики connection pools через HealthService");
        return healthService.getBasicConnectionPoolStats();
    }

    /**
     * Проверить есть ли проблемы с connection pool
     */
    public boolean hasConnectionPoolIssues() {
        return healthService.hasConnectionPoolIssues();
    }

    // ==================== ДЕЛЕГАЦИЯ К DIAGNOSTICS SERVICE ====================

    /**
     * Получение детальной статистики БД с улучшенной диагностикой
     */
    public Map<String, Object> getDatabaseDetailedStats() {
        log.info("🔍 ДЕЛЕГАЦИЯ: Получение детальной диагностики через DiagnosticsService");
        return diagnosticsService.getDatabaseDetailedStats();
    }

    // ==================== ДЕЛЕГАЦИЯ К LOGGING SERVICE ====================

    /**
     * Периодический мониторинг connection pools каждые 5 минут
     */
    @Scheduled(fixedRate = 300000) // 5 минут
    public void monitorConnectionPools() {
        try {
            log.debug("🔍 ДЕЛЕГАЦИЯ: Периодический мониторинг через LoggingService");
            loggingService.monitorConnectionPools();

            // Дополнительная проверка критических проблем
            if (healthService.hasConnectionPoolIssues()) {
                loggingService.logCriticalIssues();
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при делегации периодического мониторинга: {}", e.getMessage());
        }
    }

    /**
     * Логирование критических проблем с пулом соединений
     */
    public void logCriticalIssues() {
        log.info("🔍 ДЕЛЕГАЦИЯ: Логирование критических проблем через LoggingService");
        loggingService.logCriticalIssues();
    }

    /**
     * Логирование детальной информации для диагностики
     */
    public void logDetailedDiagnostics() {
        log.info("🔍 ДЕЛЕГАЦИЯ: Детальная диагностика через LoggingService");
        loggingService.logDetailedDiagnostics();
    }

    /**
     * Проверить и логировать состояние пула при запуске
     */
    public void logStartupPoolStatus() {
        log.info("🔍 ДЕЛЕГАЦИЯ: Проверка пула при запуске через LoggingService");
        loggingService.logStartupPoolStatus();
    }

    // ==================== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ДЛЯ СОВМЕСТИМОСТИ
    // ====================

    /**
     * Получить расширенную информацию о состоянии пулов
     * Объединяет данные от всех специализированных сервисов
     */
    public Map<String, Object> getComprehensivePoolInfo() {
        log.info("🔍 КОМПЛЕКСНАЯ ДИАГНОСТИКА: Сбор данных от всех сервисов");

        Map<String, Object> comprehensiveInfo = healthService.getBasicConnectionPoolStats();

        try {
            // Добавляем детальную диагностику
            Map<String, Object> detailedStats = diagnosticsService.getDatabaseDetailedStats();
            comprehensiveInfo.put("detailedDiagnostics", detailedStats);

            // Добавляем статус проблем
            comprehensiveInfo.put("hasIssues", healthService.hasConnectionPoolIssues());

            // Добавляем health check статус
            Health health = healthService.health();
            comprehensiveInfo.put("healthStatus", health.getStatus().getCode());
            comprehensiveInfo.put("healthDetails", health.getDetails());

            log.info("✅ КОМПЛЕКСНАЯ ДИАГНОСТИКА: Данные успешно собраны от всех сервисов");

        } catch (Exception e) {
            log.error("❌ КОМПЛЕКСНАЯ ДИАГНОСТИКА: Ошибка при сборе данных: {}", e.getMessage());
            comprehensiveInfo.put("error", "Ошибка при получении комплексной информации: " + e.getMessage());
        }

        return comprehensiveInfo;
    }

    /**
     * Выполнить полную диагностику и логирование
     * Полезно для ручной диагностики проблем
     */
    public void performFullDiagnostics() {
        log.info("🔍 ПОЛНАЯ ДИАГНОСТИКА: Начало комплексной проверки");

        try {
            // 1. Базовая проверка health
            Health health = healthService.health();
            log.info("📊 Health Status: {}", health.getStatus().getCode());

            // 2. Проверка наличия проблем
            boolean hasIssues = healthService.hasConnectionPoolIssues();
            log.info("⚠️ Has Issues: {}", hasIssues);

            // 3. Детальное логирование
            loggingService.logDetailedDiagnostics();

            // 4. Если есть проблемы - логируем критические
            if (hasIssues) {
                loggingService.logCriticalIssues();
            }

            // 5. Получаем детальную диагностику
            Map<String, Object> detailedStats = diagnosticsService.getDatabaseDetailedStats();
            log.info("📈 Detailed Stats Keys: {}", detailedStats.keySet());

            log.info("✅ ПОЛНАЯ ДИАГНОСТИКА: Завершена успешно");

        } catch (Exception e) {
            log.error("❌ ПОЛНАЯ ДИАГНОСТИКА: Ошибка выполнения: {}", e.getMessage(), e);
        }
    }

    // ==================== BACKWARD COMPATIBILITY МЕТОДЫ ====================
    // Сохранены для совместимости с существующим кодом

    /**
     * @deprecated Используйте healthService.health() напрямую
     */
    @Deprecated
    public Map<String, Object> getLegacyHealthInfo() {
        log.warn("⚠️ DEPRECATED: Используется устаревший метод getLegacyHealthInfo()");
        Health health = healthService.health();
        return health.getDetails();
    }

    /**
     * @deprecated Используйте loggingService.monitorConnectionPools() напрямую
     */
    @Deprecated
    public void performLegacyMonitoring() {
        log.warn("⚠️ DEPRECATED: Используется устаревший метод performLegacyMonitoring()");
        loggingService.monitorConnectionPools();
    }
}
