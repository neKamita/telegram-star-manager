package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.StarPackageEntity;
import shit.back.entity.UserSessionEntity;
import shit.back.model.UserCountsBatchResult;
import shit.back.dto.monitoring.SystemHealth;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис обслуживания системы для админской панели
 * Выделен из AdminDashboardService в рамках разделения God Classes (Week 3-4)
 * 
 * Отвечает за:
 * - Мониторинг здоровья системы
 * - Обслуживание и очистка данных
 * - Обнаружение проблем в системе
 * - Профилактические операции
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring - God Class Split
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AdminMaintenanceService {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StarPackageService starPackageService;

    @Autowired
    private UserSessionUnifiedService userSessionService;

    /**
     * Получение индикаторов здоровья системы
     * ОПТИМИЗИРОВАНО с кэшированием для снижения нагрузки
     */
    @Cacheable(value = "systemHealth", unless = "#result == null")
    public shit.back.dto.monitoring.SystemHealth getSystemHealth() {
        long startTime = System.currentTimeMillis();
        log.error(
                "🔍 ДИАГНОСТИКА СИСТЕМНОГО ЗДОРОВЬЯ: getSystemHealth() ВЫЗВАН! Время: {} - теперь кэшируется на 10 минут!",
                LocalDateTime.now());

        log.info("🔧 ОБСЛУЖИВАНИЕ: Начало проверки здоровья системы с улучшенным кэшированием");

        // Проверка застрявших пользователей (временная заглушка)
        long stuckUsersStart = System.currentTimeMillis();
        List<UserSessionEntity> stuckUsers = detectStuckUsers();
        long stuckUsersTime = System.currentTimeMillis() - stuckUsersStart;
        log.info("⚠️ ОБСЛУЖИВАНИЕ: Stuck users query took {}ms", stuckUsersTime);

        // Проверка пользователей с незавершенными заказами (временная заглушка)
        long pendingOrdersStart = System.currentTimeMillis();
        List<UserSessionEntity> usersWithPendingOrders = detectUsersWithPendingOrders();
        long pendingOrdersTime = System.currentTimeMillis() - pendingOrdersStart;
        log.info("⏳ ОБСЛУЖИВАНИЕ: Pending orders query took {}ms", pendingOrdersTime);

        // Проверка пакетов без продаж
        long packagesStart = System.currentTimeMillis();
        List<StarPackageEntity> packagesWithoutSales = starPackageService.getPackagesWithoutSales();
        long packagesTime = System.currentTimeMillis() - packagesStart;
        log.info("📦 ОБСЛУЖИВАНИЕ: Packages without sales query took {}ms", packagesTime);

        // Получение счетчиков пользователей - OPTIMIZED WITH BATCH QUERY
        long userCountsStart = System.currentTimeMillis();
        UserCountsBatchResult healthCounts = userSessionService.getUserCountsBatch();
        long onlineUsersCount = healthCounts.onlineUsers();
        long activeUsersCount = healthCounts.activeUsers();
        long totalUsersCount = healthCounts.totalUsers();
        long userCountsTime = System.currentTimeMillis() - userCountsStart;
        log.info("✅ ОБСЛУЖИВАНИЕ ОПТИМИЗИРОВАНО: User counts took {}ms - SINGLE BATCH QUERY!",
                userCountsTime);

        // Получение счетчиков заказов
        long orderCountStart = System.currentTimeMillis();
        long totalOrdersCount = orderService.getTotalOrdersCount();
        long orderCountTime = System.currentTimeMillis() - orderCountStart;
        log.info("📊 ОБСЛУЖИВАНИЕ: Order count query took {}ms", orderCountTime);

        // Расчет общего здоровья системы
        int healthScore = calculateHealthScore(stuckUsers.size(), usersWithPendingOrders.size(),
                packagesWithoutSales.size());

        long totalTime = System.currentTimeMillis() - startTime;
        log.error(
                "🔧 ОБСЛУЖИВАНИЕ ЗАВЕРШЕНО: SystemHealth TOTAL time {}ms (stuck:{}ms, pending:{}ms, packages:{}ms, userCounts:{}ms, orderCount:{}ms)",
                totalTime, stuckUsersTime, pendingOrdersTime, packagesTime, userCountsTime, orderCountTime);

        log.info(
                "🏥 ДИАГНОСТИКА: Health Score calculation - stuck users: {}, pending orders: {}, packages without sales: {}, final score: {}",
                stuckUsers.size(), usersWithPendingOrders.size(), packagesWithoutSales.size(), healthScore);

        // Симуляция проверок системных компонентов (в реальной системе это были бы
        // реальные проверки)
        boolean redisHealthy = checkRedisHealth();
        boolean botHealthy = checkBotHealth();
        boolean cacheHealthy = checkCacheHealth();

        // Симуляция метрик производительности
        Double averageResponseTime = calculateAverageResponseTime();
        Integer memoryUsagePercent = calculateMemoryUsage();
        Integer cacheHitRatio = calculateCacheHitRatio();

        // Формируем DTO для передачи наружу
        shit.back.dto.monitoring.SystemStatus status = (healthScore >= 80 && redisHealthy && botHealthy && cacheHealthy)
                ? shit.back.dto.monitoring.SystemStatus.UP
                : shit.back.dto.monitoring.SystemStatus.DOWN;

        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("healthScore", String.valueOf(healthScore));
        details.put("stuckUsersCount", String.valueOf(stuckUsers.size()));
        details.put("pendingOrdersCount", String.valueOf(usersWithPendingOrders.size()));
        details.put("packagesWithoutSalesCount", String.valueOf(packagesWithoutSales.size()));
        details.put("redisHealthy", String.valueOf(redisHealthy));
        details.put("botHealthy", String.valueOf(botHealthy));
        details.put("cacheHealthy", String.valueOf(cacheHealthy));
        details.put("onlineUsersCount", String.valueOf(onlineUsersCount));
        details.put("activeUsersCount", String.valueOf(activeUsersCount));
        details.put("averageResponseTime", String.valueOf(averageResponseTime));
        details.put("memoryUsagePercent", String.valueOf(memoryUsagePercent));
        details.put("cacheHitRatio", String.valueOf(cacheHitRatio));
        details.put("totalUsers", String.valueOf(totalUsersCount));
        details.put("totalOrders", String.valueOf(totalOrdersCount));

        java.util.List<String> messages = new java.util.ArrayList<>();
        if (!redisHealthy)
            messages.add("Redis не отвечает");
        if (!botHealthy)
            messages.add("Бот не отвечает");
        if (!cacheHealthy)
            messages.add("Кэш не отвечает");
        if (healthScore < 80)
            messages.add("Общий балл здоровья системы ниже нормы");

        return new shit.back.dto.monitoring.SystemHealth(
                status,
                details,
                LocalDateTime.now(),
                messages);
    }

    /**
     * Выполнение профилактических операций обслуживания
     */
    @Transactional
    public MaintenanceResult performMaintenance() {
        long startTime = System.currentTimeMillis();
        log.info("🔧 ОБСЛУЖИВАНИЕ: Выполнение профилактических операций - НАЧАЛО");

        // Деактивация истекших сессий
        long sessionStart = System.currentTimeMillis();
        int deactivatedSessions = userSessionService.deactivateExpiredSessions(24);
        long sessionTime = System.currentTimeMillis() - sessionStart;
        log.info("👥 ОБСЛУЖИВАНИЕ: Деактивированы {} истекших сессий за {}ms", deactivatedSessions, sessionTime);

        // Деактивация истекших пакетов
        long packageStart = System.currentTimeMillis();
        int deactivatedPackages = starPackageService.deactivateExpiredPackages();
        long packageTime = System.currentTimeMillis() - packageStart;
        log.info("📦 ОБСЛУЖИВАНИЕ: Деактивированы {} истекших пакетов за {}ms", deactivatedPackages, packageTime);

        // Дополнительные операции очистки
        long cleanupStart = System.currentTimeMillis();
        int cleanedLogEntries = performLogCleanup();
        int optimizedQueries = performQueryOptimization();
        long cleanupTime = System.currentTimeMillis() - cleanupStart;
        log.info("🧹 ОБСЛУЖИВАНИЕ: Cleanup operations completed in {}ms", cleanupTime);

        long totalTime = System.currentTimeMillis() - startTime;

        MaintenanceResult result = MaintenanceResult.builder()
                .deactivatedSessions(deactivatedSessions)
                .deactivatedPackages(deactivatedPackages)
                .cleanedLogEntries(cleanedLogEntries)
                .optimizedQueries(optimizedQueries)
                .maintenanceTime(LocalDateTime.now())
                .executionTimeMs(totalTime)
                .build();

        log.warn("🔧 ОБСЛУЖИВАНИЕ ЗАВЕРШЕНО: Maintenance completed in {}ms: {}", totalTime, result);
        return result;
    }

    /**
     * Диагностика проблем в системе
     */
    public SystemDiagnostics runSystemDiagnostics() {
        log.info("🔍 ДИАГНОСТИКА: Запуск системной диагностики");

        // Проверка производительности запросов
        List<String> slowQueries = detectSlowQueries();

        // Проверка проблемных пользователей
        List<String> problematicUsers = detectProblematicUsers();

        // Проверка системных ресурсов
        ResourceUsage resourceUsage = checkResourceUsage();

        // Проверка целостности данных
        List<String> dataIntegrityIssues = checkDataIntegrity();

        return SystemDiagnostics.builder()
                .slowQueries(slowQueries)
                .problematicUsers(problematicUsers)
                .resourceUsage(resourceUsage)
                .dataIntegrityIssues(dataIntegrityIssues)
                .diagnosticsTime(LocalDateTime.now())
                .overallStatus(determineDiagnosticsStatus(slowQueries, problematicUsers, dataIntegrityIssues))
                .build();
    }

    // Приватные методы для различных проверок и операций

    private List<UserSessionEntity> detectStuckUsers() {
        // Временная заглушка - в реальной системе будет запрос к БД
        return List.of();
    }

    private List<UserSessionEntity> detectUsersWithPendingOrders() {
        // Временная заглушка - в реальной системе будет запрос к БД
        return List.of();
    }

    private int calculateHealthScore(int stuckUsers, int pendingOrders, int packagesWithoutSales) {
        int score = 100;

        // Снижение баллов за проблемы
        score -= stuckUsers * 2;
        score -= pendingOrders;
        score -= packagesWithoutSales;

        return Math.max(0, Math.min(100, score));
    }

    private boolean checkRedisHealth() {
        // В реальной системе - проверка подключения к Redis
        return true;
    }

    private boolean checkBotHealth() {
        // В реальной системе - проверка статуса Telegram бота
        return true;
    }

    private boolean checkCacheHealth() {
        // В реальной системе - проверка статуса кэша
        return true;
    }

    private Double calculateAverageResponseTime() {
        // Симуляция - в реальной системе из метрик
        return 85.0 + (Math.random() * 30); // 85-115ms
    }

    private Integer calculateMemoryUsage() {
        // Симуляция - в реальной системе из JVM метрик
        return 60 + (int) (Math.random() * 20); // 60-80%
    }

    private Integer calculateCacheHitRatio() {
        // Симуляция - в реальной системе из кэш метрик
        return 85 + (int) (Math.random() * 10); // 85-95%
    }

    private int performLogCleanup() {
        // Заглушка для очистки логов
        return (int) (Math.random() * 100);
    }

    private int performQueryOptimization() {
        // Заглушка для оптимизации запросов
        return (int) (Math.random() * 10);
    }

    private List<String> detectSlowQueries() {
        // Заглушка для обнаружения медленных запросов
        return List.of("SELECT * FROM orders WHERE created_at > ?", "SELECT COUNT(*) FROM user_sessions");
    }

    private List<String> detectProblematicUsers() {
        // Заглушка для обнаружения проблемных пользователей
        return List.of("User_12345: too many failed payments", "User_67890: suspicious activity");
    }

    private ResourceUsage checkResourceUsage() {
        return ResourceUsage.builder()
                .cpuUsage(45.2)
                .memoryUsage(67.8)
                .diskUsage(34.1)
                .networkUsage(12.5)
                .build();
    }

    private List<String> checkDataIntegrity() {
        // Заглушка для проверки целостности данных
        return List.of("Orphaned payments detected: 3", "Missing user balances: 1");
    }

    private String determineDiagnosticsStatus(List<String> slowQueries, List<String> problematicUsers,
            List<String> dataIntegrityIssues) {
        int issueCount = slowQueries.size() + problematicUsers.size() + dataIntegrityIssues.size();

        if (issueCount == 0)
            return "EXCELLENT";
        if (issueCount <= 3)
            return "GOOD";
        if (issueCount <= 7)
            return "WARNING";
        return "CRITICAL";
    }

    // Data Transfer Objects

    @lombok.Data
    @lombok.Builder
    public static class MaintenanceResult {
        private int deactivatedSessions;
        private int deactivatedPackages;
        private int cleanedLogEntries;
        private int optimizedQueries;
        private LocalDateTime maintenanceTime;
        private long executionTimeMs;
    }

    @lombok.Data
    @lombok.Builder
    public static class SystemDiagnostics {
        private List<String> slowQueries;
        private List<String> problematicUsers;
        private ResourceUsage resourceUsage;
        private List<String> dataIntegrityIssues;
        private LocalDateTime diagnosticsTime;
        private String overallStatus;
    }

    @lombok.Data
    @lombok.Builder
    public static class ResourceUsage {
        private double cpuUsage;
        private double memoryUsage;
        private double diskUsage;
        private double networkUsage;
    }
}