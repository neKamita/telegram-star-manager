package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.annotation.Auditable;
import shit.back.entity.OrderEntity;
import shit.back.entity.StarPackageEntity;
import shit.back.entity.UserSessionEntity;
import shit.back.model.UserCountsBatchResult;
import shit.back.dto.order.OrderStatistics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Основной сервис админской панели
 * РЕФАКТОРИНГ Week 3-4: Разделение God Classes
 * 
 * Теперь делегирует специализированные задачи:
 * - AdminAnalyticsService - аналитика и метрики
 * - AdminMaintenanceService - обслуживание системы
 * 
 * Сохраняет только:
 * - Агрегацию основных данных дашборда
 * - Интеграцию между сервисами
 * - Простые операции с данными
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring - God Class Split
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StarPackageService starPackageService;

    @Autowired
    private UserSessionUnifiedService userSessionService;

    // === НОВЫЕ РАЗДЕЛЕННЫЕ СЕРВИСЫ (Week 3-4) ===
    @Autowired
    private AdminAnalyticsService adminAnalyticsService;

    @Autowired
    private AdminMaintenanceService adminMaintenanceService;

    /**
     * Получение общего обзора дашборда
     * ОПТИМИЗИРОВАНО: использует batch query
     */
    @Auditable(description = "Получение обзора административной панели", auditType = Auditable.AuditType.ADMIN)
    public DashboardOverview getDashboardOverview() {
        long startTime = System.currentTimeMillis();
        log.info("📊 ДАШБОРД: Генерация обзора дашборда - НАЧАЛО");

        // Получение статистики заказов
        long orderStatsStart = System.currentTimeMillis();
        OrderStatistics orderStats = orderService.getOrderStatistics();
        long orderStatsTime = System.currentTimeMillis() - orderStatsStart;
        log.info("📦 ДАШБОРД: Order statistics took {}ms", orderStatsTime);

        // Получение статистики пакетов
        long packageStatsStart = System.currentTimeMillis();
        StarPackageService.PackageStatistics packageStats = starPackageService.getPackageStatistics();
        long packageStatsTime = System.currentTimeMillis() - packageStatsStart;
        log.info("⭐ ДАШБОРД: Package statistics took {}ms", packageStatsTime);

        // Получение статистики пользователей
        long userStatsStart = System.currentTimeMillis();
        UserSessionUnifiedService.UserSessionStatistics userStats = userSessionService.getUserSessionStatistics();
        long userStatsTime = System.currentTimeMillis() - userStatsStart;
        log.info("👥 ДАШБОРД: User session statistics took {}ms", userStatsTime);

        // Получение прямых счетчиков пользователей - OPTIMIZED BATCH QUERY
        long userCountsStart = System.currentTimeMillis();
        UserCountsBatchResult userCounts = userSessionService.getUserCountsBatch();
        long totalUsersCount = userCounts.totalUsers();
        long activeUsersCount = userCounts.activeUsers();
        long onlineUsersCount = userCounts.onlineUsers();
        long userCountsTime = System.currentTimeMillis() - userCountsStart;

        log.info(
                "✅ ОПТИМИЗАЦИЯ N+1 РЕШЕНА: SINGLE BATCH QUERY took {}ms instead of 3 separate queries! Total={}, Active={}, Online={}",
                userCountsTime, totalUsersCount, activeUsersCount, onlineUsersCount);

        long totalTime = System.currentTimeMillis() - startTime;
        log.warn("📊 ДАШБОРД: Dashboard overview TOTAL time {}ms (order:{}ms, package:{}ms, user:{}ms, counts:{}ms)",
                totalTime, orderStatsTime, packageStatsTime, userStatsTime, userCountsTime);

        return DashboardOverview.builder()
                .orderStatistics(orderStats)
                .packageStatistics(packageStats)
                .userStatistics(userStats)
                .totalUsersCount(totalUsersCount)
                .activeUsersCount(activeUsersCount)
                .onlineUsersCount(onlineUsersCount)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * Получение недавней активности
     */
    @Auditable(description = "Получение недавней активности", auditType = Auditable.AuditType.ADMIN)
    public RecentActivity getRecentActivity() {
        long startTime = System.currentTimeMillis();
        log.info("📈 ДАШБОРД: Получение недавней активности - НАЧАЛО");

        long recentOrdersStart = System.currentTimeMillis();
        List<OrderEntity> recentOrders = orderService.getRecentOrders(7); // Last 7 days
        long recentOrdersTime = System.currentTimeMillis() - recentOrdersStart;

        long recentUsersStart = System.currentTimeMillis();
        long recentUsersCount = userSessionService.getNewUsersCount(7); // Last 7 days
        long recentUsersTime = System.currentTimeMillis() - recentUsersStart;

        long onlineUsersStart = System.currentTimeMillis();
        List<UserSessionEntity> onlineUsers = userSessionService.getOnlineUsers();
        long onlineUsersTime = System.currentTimeMillis() - onlineUsersStart;

        long todaysOrdersStart = System.currentTimeMillis();
        List<OrderEntity> todaysOrders = orderService.getTodaysOrders();
        long todaysOrdersTime = System.currentTimeMillis() - todaysOrdersStart;

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("📈 ДАШБОРД: Recent activity TOTAL time {}ms (recent:{}ms, users:{}ms, online:{}ms, today:{}ms)",
                totalTime, recentOrdersTime, recentUsersTime, onlineUsersTime, todaysOrdersTime);

        return RecentActivity.builder()
                .recentOrders(recentOrders.stream().limit(10).toList())
                .newUsers(java.util.Collections.emptyList())
                .onlineUsers(onlineUsers.stream().limit(10).toList())
                .todaysOrders(todaysOrders)
                .totalRecentOrders(recentOrders.size())
                .totalNewUsers((int) recentUsersCount)
                .totalOnlineUsers(onlineUsers.size())
                .totalTodaysOrders(todaysOrders.size())
                .build();
    }

    /**
     * Получение комбинированной недавней активности
     */
    public CombinedRecentActivity getCombinedRecentActivity() {
        log.info("📊 ДАШБОРД: Получение комбинированной недавней активности (только заказы)");

        List<OrderEntity> recentOrders = orderService.getRecentOrders(30); // Last 30 days
        List<ActivityItem> allActivities = new ArrayList<>();

        // Добавляем заказы в активности
        for (OrderEntity order : recentOrders) {
            ActivityItem item = ActivityItem.builder()
                    .type("ORDER")
                    .title("Purchase: " + order.getStarCount() + " Stars")
                    .description("User " + order.getUserId() + " bought " + order.getStarCount() + " stars for $"
                            + order.getFinalAmount())
                    .timestamp(order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now())
                    .icon("fas fa-shopping-cart")
                    .badgeClass("badge bg-success")
                    .badgeText(order.getStatus() != null ? order.getStatus().toString() : "COMPLETED")
                    .actionUrl("/admin-legacy/orders/" + order.getOrderId())
                    .metadata(Map.of(
                            "userId", order.getUserId().toString(),
                            "amount", order.getStarCount().toString(),
                            "price", order.getFinalAmount().toString(),
                            "orderId", order.getOrderId().toString()))
                    .build();
            allActivities.add(item);
        }

        // Сортируем по времени и ограничиваем
        List<ActivityItem> sortedActivities = allActivities.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(20)
                .collect(Collectors.toList());

        return CombinedRecentActivity.builder()
                .activities(sortedActivities)
                .totalActivities(sortedActivities.size())
                .orderCount(sortedActivities.size())
                .flagCount(0) // Feature Flags удалены
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    // === ДЕЛЕГАЦИЯ К НОВЫМ СЕРВИСАМ (Week 3-4) ===

    /**
     * Получение метрик производительности
     * ДЕЛЕГИРУЕТ к AdminAnalyticsService
     */
    @Auditable(description = "Получение метрик производительности", auditType = Auditable.AuditType.ADMIN)
    public shit.back.dto.monitoring.PerformanceMetrics getPerformanceMetrics() {
        log.info("📊 ДАШБОРД: Делегация получения метрик производительности к AdminAnalyticsService");
        return adminAnalyticsService.getPerformanceMetrics();
    }

    /**
     * Получение топ-исполнителей
     * ДЕЛЕГИРУЕТ к AdminAnalyticsService
     */
    public AdminAnalyticsService.TopPerformers getTopPerformers() {
        log.info("🏆 ДАШБОРД: Делегация получения топ-исполнителей к AdminAnalyticsService");
        return adminAnalyticsService.getTopPerformers();
    }

    /**
     * Получение аналитических данных
     * ДЕЛЕГИРУЕТ к AdminAnalyticsService
     */
    public AdminAnalyticsService.AnalyticsData getAnalyticsData(int days) {
        log.info("📈 ДАШБОРД: Делегация получения аналитических данных к AdminAnalyticsService");
        return adminAnalyticsService.getAnalyticsData(days);
    }

    /**
     * Получение здоровья системы
     * ДЕЛЕГИРУЕТ к AdminMaintenanceService
     */
    @Auditable(description = "Проверка здоровья системы", auditType = Auditable.AuditType.ADMIN)
    public shit.back.dto.monitoring.SystemHealth getSystemHealth() {
        log.info("🔧 ДАШБОРД: Делегация проверки здоровья системы к AdminMaintenanceService");
        return adminMaintenanceService.getSystemHealth();
    }

    /**
     * Выполнение обслуживания
     * ДЕЛЕГИРУЕТ к AdminMaintenanceService
     */
    @Auditable(description = "Выполнение системного обслуживания", auditType = Auditable.AuditType.CRITICAL)
    @Transactional
    public AdminMaintenanceService.MaintenanceResult performMaintenance() {
        log.info("🔧 ДАШБОРД: Делегация выполнения обслуживания к AdminMaintenanceService");
        return adminMaintenanceService.performMaintenance();
    }

    // === ПРОСТЫЕ ОПЕРАЦИИ С ДАННЫМИ ===

    /**
     * Получение заказов с поиском
     */
    public Page<OrderEntity> getOrdersWithSearch(String searchTerm, Pageable pageable) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return orderService.searchOrders(searchTerm.trim(), pageable);
        }
        return orderService.getOrders(pageable);
    }

    /**
     * Получение пользователей с поиском
     */
    public Page<UserSessionEntity> getUsersWithSearch(String searchTerm, Pageable pageable) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return userSessionService.searchSessions(searchTerm.trim(), pageable);
        }
        return userSessionService.getSessions(pageable);
    }

    /**
     * Получение пакетов с пагинацией
     */
    public Page<StarPackageEntity> getPackages(Pageable pageable) {
        return starPackageService.getPackages(pageable);
    }

    // Data Transfer Objects (сохранены для backward compatibility)

    @lombok.Data
    @lombok.Builder
    public static class DashboardOverview {
        private OrderStatistics orderStatistics;
        private StarPackageService.PackageStatistics packageStatistics;
        private UserSessionUnifiedService.UserSessionStatistics userStatistics;
        private LocalDateTime lastUpdated;

        // Прямые счетчики пользователей для удобного доступа
        private long totalUsersCount;
        private long activeUsersCount;
        private long onlineUsersCount;
    }

    @lombok.Data
    @lombok.Builder
    public static class RecentActivity {
        private List<OrderEntity> recentOrders;
        private List<UserSessionEntity> newUsers;
        private List<UserSessionEntity> onlineUsers;
        private List<OrderEntity> todaysOrders;
        private int totalRecentOrders;
        private int totalNewUsers;
        private int totalOnlineUsers;
        private int totalTodaysOrders;
    }

    @lombok.Data
    @lombok.Builder
    public static class CombinedRecentActivity {
        private List<ActivityItem> activities;
        private int totalActivities;
        private int orderCount;
        private int flagCount;
        private LocalDateTime lastUpdated;
    }

    @lombok.Data
    @lombok.Builder
    public static class ActivityItem {
        private String type; // "ORDER" или "FEATURE_FLAG"
        private String title;
        private String description;
        private LocalDateTime timestamp;
        private String icon; // CSS класс иконки
        private String badgeClass; // CSS класс для статуса
        private String badgeText;
        private String actionUrl; // Ссылка для просмотра
        private Map<String, String> metadata; // Дополнительные данные
    }

}
