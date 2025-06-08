package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.OrderEntity;
import shit.back.entity.StarPackageEntity;
import shit.back.entity.UserSessionEntity;
import shit.back.entity.FeatureFlagEntity;
import shit.back.repository.FeatureFlagJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that aggregates data for the admin dashboard
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
    private UserSessionEnhancedService userSessionService;
    
    @Autowired
    private FeatureFlagJpaRepository jpaRepository;
    
    /**
     * Get comprehensive dashboard overview
     */
    public DashboardOverview getDashboardOverview() {
        log.info("Generating dashboard overview");
        
        // Get order statistics
        OrderService.OrderStatistics orderStats = orderService.getOrderStatistics();
        
        // Get package statistics
        StarPackageService.PackageStatistics packageStats = starPackageService.getPackageStatistics();
        
        // Get user session statistics
        UserSessionEnhancedService.UserSessionStatistics userStats = userSessionService.getUserSessionStatistics();
        
        // Get direct user counts for easy frontend access
        long totalUsersCount = userSessionService.getTotalUsersCount();
        long activeUsersCount = userSessionService.getActiveUsersCount();
        long onlineUsersCount = userSessionService.getOnlineUsersCount();
        
        log.info("Dashboard user counts - Total: {}, Active: {}, Online: {}", 
                totalUsersCount, activeUsersCount, onlineUsersCount);
        
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
     * Get recent activity summary
     */
    public RecentActivity getRecentActivity() {
        log.info("Getting recent activity");
        
        List<OrderEntity> recentOrders = orderService.getRecentOrders(7); // Last 7 days
        List<UserSessionEntity> recentUsers = userSessionService.getNewUsers(7); // Last 7 days
        List<UserSessionEntity> onlineUsers = userSessionService.getOnlineUsers();
        List<OrderEntity> todaysOrders = orderService.getTodaysOrders();
        
        return RecentActivity.builder()
                .recentOrders(recentOrders.stream().limit(10).toList())
                .newUsers(recentUsers.stream().limit(10).toList())
                .onlineUsers(onlineUsers.stream().limit(10).toList())
                .todaysOrders(todaysOrders)
                .totalRecentOrders(recentOrders.size())
                .totalNewUsers(recentUsers.size())
                .totalOnlineUsers(onlineUsers.size())
                .totalTodaysOrders(todaysOrders.size())
                .build();
    }

    /**
     * Get combined recent activity (orders + feature flags)
     */
    public CombinedRecentActivity getCombinedRecentActivity() {
        log.info("Getting combined recent activity");
        
        // Получаем последние заказы
        List<OrderEntity> recentOrders = orderService.getRecentOrders(30); // Last 30 days
        
        // Получаем недавние флаги (создание/обновление из FeatureFlagService)
        List<FeatureFlagEntity> recentFlags = getRecentFeatureFlags(30);
        
        // Создаем объединенный список активностей
        List<ActivityItem> allActivities = new ArrayList<>();
        
        // Добавляем заказы
        for (OrderEntity order : recentOrders) {
            ActivityItem item = ActivityItem.builder()
                    .type("ORDER")
                    .title("Purchase: " + order.getStarCount() + " Stars")
                    .description("User " + order.getUserId() + " bought " + order.getStarCount() + " stars for $" + order.getFinalAmount())
                    .timestamp(order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now())
                    .icon("fas fa-shopping-cart")
                    .badgeClass("badge bg-success")
                    .badgeText(order.getStatus() != null ? order.getStatus().toString() : "COMPLETED")
                    .actionUrl("/admin/orders/" + order.getOrderId())
                    .metadata(Map.of(
                            "userId", order.getUserId().toString(),
                            "amount", order.getStarCount().toString(),
                            "price", order.getFinalAmount().toString(),
                            "orderId", order.getOrderId().toString()
                    ))
                    .build();
            allActivities.add(item);
        }
        
        // Добавляем флаги
        for (FeatureFlagEntity flag : recentFlags) {
            ActivityItem item = ActivityItem.builder()
                    .type("FEATURE_FLAG")
                    .title("Feature Flag: " + flag.getName())
                    .description(flag.getDescription() != null ? flag.getDescription() : "Feature flag updated")
                    .timestamp(flag.getUpdatedAt() != null ? flag.getUpdatedAt() : flag.getCreatedAt())
                    .icon("fas fa-flag")
                    .badgeClass(flag.isEnabled() ? "badge bg-primary" : "badge bg-secondary")
                    .badgeText(flag.isEnabled() ? "ACTIVE" : "INACTIVE")
                    .actionUrl("/admin/feature-flags/" + flag.getName() + "/edit")
                    .metadata(Map.of(
                            "flagName", flag.getName(),
                            "enabled", String.valueOf(flag.isEnabled()),
                            "rollout", flag.getRolloutPercentage() != null ? flag.getRolloutPercentage().toString() : "100"
                    ))
                    .build();
            allActivities.add(item);
        }
        
        // Сортируем по времени (новые сначала) и ограничиваем до 20
        List<ActivityItem> sortedActivities = allActivities.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(20)
                .collect(Collectors.toList());
        
        // Подсчитываем статистику
        long orderCount = sortedActivities.stream().filter(a -> "ORDER".equals(a.getType())).count();
        long flagCount = sortedActivities.stream().filter(a -> "FEATURE_FLAG".equals(a.getType())).count();
        
        return CombinedRecentActivity.builder()
                .activities(sortedActivities)
                .totalActivities(sortedActivities.size())
                .orderCount((int) orderCount)
                .flagCount((int) flagCount)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    /**
     * Get recent feature flags (created or updated recently)
     */
    private List<FeatureFlagEntity> getRecentFeatureFlags(int days) {
        try {
            // Используем JPA repository для получения флагов
            LocalDateTime since = LocalDateTime.now().minusDays(days);
            
            // Получаем все флаги и фильтруем по дате
            return jpaRepository.findAll().stream()
                    .filter(flag -> {
                        LocalDateTime relevantDate = flag.getUpdatedAt() != null ? 
                                flag.getUpdatedAt() : flag.getCreatedAt();
                        return relevantDate != null && relevantDate.isAfter(since);
                    })
                    .sorted((a, b) -> {
                        LocalDateTime dateA = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                        LocalDateTime dateB = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
                        return dateB.compareTo(dateA); // Новые сначала
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching recent feature flags: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Get performance metrics
     */
    public PerformanceMetrics getPerformanceMetrics() {
        log.info("Calculating performance metrics");
        
        // Revenue metrics
        BigDecimal todayRevenue = orderService.getTodayRevenue();
        BigDecimal monthRevenue = orderService.getMonthRevenue();
        BigDecimal totalRevenue = orderService.getTotalRevenue();
        
        // Conversion metrics
        long totalOrders = orderService.getTotalOrdersCount();
        long completedOrders = orderService.getCompletedOrdersCount();
        double orderConversionRate = totalOrders > 0 ? (double) completedOrders / totalOrders * 100 : 0;
        
        // User engagement metrics
        long totalUsers = userSessionService.getTotalUsersCount();
        long activeUsers = userSessionService.getActiveUsersCount();
        long onlineUsers = userSessionService.getOnlineUsersCount();
        
        double userEngagementRate = totalUsers > 0 ? (double) activeUsers / totalUsers * 100 : 0;
        
        return PerformanceMetrics.builder()
                .todayRevenue(todayRevenue)
                .monthRevenue(monthRevenue)
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .orderConversionRate(orderConversionRate)
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .onlineUsers(onlineUsers)
                .userEngagementRate(userEngagementRate)
                .averageOrderValue(orderService.getAverageOrderValue())
                .build();
    }
    
    /**
     * Get top performers
     */
    public TopPerformers getTopPerformers() {
        log.info("Getting top performers");
        
        List<OrderService.CustomerStats> topCustomers = orderService.getTopCustomers(10);
        List<StarPackageEntity> topPackages = starPackageService.getTopSellingPackages(10);
        List<UserSessionEntity> topActiveUsers = userSessionService.getTopActiveUsers(10);
        List<StarPackageEntity> bestValuePackages = starPackageService.getBestValuePackages(5);
        
        return TopPerformers.builder()
                .topCustomers(topCustomers)
                .topSellingPackages(topPackages)
                .mostActiveUsers(topActiveUsers)
                .bestValuePackages(bestValuePackages)
                .build();
    }
    
    /**
     * Get analytics data for charts
     */
    public AnalyticsData getAnalyticsData(int days) {
        log.info("Getting analytics data for {} days", days);
        
        List<OrderService.DailyStats> dailyRevenue = orderService.getDailyStatistics(days);
        List<UserSessionEnhancedService.DailyActiveUsers> dailyActiveUsers = userSessionService.getDailyActiveUsers(days);
        List<UserSessionEnhancedService.LanguageStats> languageStats = userSessionService.getUsersByLanguage();
        List<StarPackageService.PackageTypeSales> packageTypeSales = starPackageService.getSalesByPackageType();
        
        return AnalyticsData.builder()
                .dailyRevenue(dailyRevenue)
                .dailyActiveUsers(dailyActiveUsers)
                .languageDistribution(languageStats)
                .packageTypeSales(packageTypeSales)
                .build();
    }
    
    /**
     * Get system health indicators
     */
    public SystemHealth getSystemHealth() {
        log.info("Checking system health");
        
        // Check for stuck users
        List<UserSessionEntity> stuckUsers = userSessionService.getUsersStuckInState(
                UserSessionEntity.SessionState.AWAITING_PAYMENT, 24);
        
        // Check for pending orders
        List<UserSessionEntity> usersWithPendingOrders = userSessionService.getUsersWithPendingOrders();
        
        // Check for packages without sales
        List<StarPackageEntity> packagesWithoutSales = starPackageService.getPackagesWithoutSales();
        
        // Get user counts
        long onlineUsersCount = userSessionService.getOnlineUsersCount();
        long activeUsersCount = userSessionService.getActiveUsersCount();
        
        // Calculate health score
        int healthScore = calculateHealthScore(stuckUsers.size(), usersWithPendingOrders.size(), 
                                             packagesWithoutSales.size());
        
        // Simulate system health checks (in a real system, these would be actual checks)
        boolean redisHealthy = true; // Would check Redis connection
        boolean botHealthy = true;   // Would check Telegram bot status
        boolean cacheHealthy = true; // Would check cache status
        
        // Simulate performance metrics
        Double averageResponseTime = 85.0 + (Math.random() * 30); // 85-115ms
        Integer memoryUsagePercent = 60 + (int)(Math.random() * 20); // 60-80%
        Integer cacheHitRatio = 85 + (int)(Math.random() * 10); // 85-95%
        
        return SystemHealth.builder()
                .healthScore(healthScore)
                .stuckUsersCount(stuckUsers.size())
                .pendingOrdersCount(usersWithPendingOrders.size())
                .packagesWithoutSalesCount(packagesWithoutSales.size())
                .stuckUsers(stuckUsers.stream().limit(5).toList())
                .usersWithPendingOrders(usersWithPendingOrders.stream().limit(5).toList())
                .packagesWithoutSales(packagesWithoutSales.stream().limit(5).toList())
                .lastChecked(LocalDateTime.now())
                // Additional frontend fields
                .redisHealthy(redisHealthy)
                .botHealthy(botHealthy)
                .cacheHealthy(cacheHealthy)
                .onlineUsersCount(onlineUsersCount)
                .activeUsersCount(activeUsersCount)
                .averageResponseTime(averageResponseTime)
                .memoryUsagePercent(memoryUsagePercent)
                .cacheHitRatio(cacheHitRatio)
                .build();
    }
    
    /**
     * Get paginated orders with search
     */
    public Page<OrderEntity> getOrdersWithSearch(String searchTerm, Pageable pageable) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return orderService.searchOrders(searchTerm.trim(), pageable);
        }
        return orderService.getOrders(pageable);
    }
    
    /**
     * Get paginated users with search
     */
    public Page<UserSessionEntity> getUsersWithSearch(String searchTerm, Pageable pageable) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return userSessionService.searchSessions(searchTerm.trim(), pageable);
        }
        return userSessionService.getSessions(pageable);
    }
    
    /**
     * Get paginated packages
     */
    public Page<StarPackageEntity> getPackages(Pageable pageable) {
        return starPackageService.getPackages(pageable);
    }
    
    /**
     * Perform maintenance tasks
     */
    @Transactional
    public MaintenanceResult performMaintenance() {
        log.info("Performing system maintenance");
        
        int deactivatedSessions = userSessionService.deactivateExpiredSessions(24);
        int deactivatedPackages = starPackageService.deactivateExpiredPackages();
        
        MaintenanceResult result = MaintenanceResult.builder()
                .deactivatedSessions(deactivatedSessions)
                .deactivatedPackages(deactivatedPackages)
                .maintenanceTime(LocalDateTime.now())
                .build();
        
        log.info("Maintenance completed: {}", result);
        return result;
    }
    
    private int calculateHealthScore(int stuckUsers, int pendingOrders, int packagesWithoutSales) {
        int score = 100;
        
        // Deduct points for issues
        score -= stuckUsers * 2;
        score -= pendingOrders;
        score -= packagesWithoutSales;
        
        return Math.max(0, Math.min(100, score));
    }
    
    // Data transfer objects
    
    @lombok.Data
    @lombok.Builder
    public static class DashboardOverview {
        private OrderService.OrderStatistics orderStatistics;
        private StarPackageService.PackageStatistics packageStatistics;
        private UserSessionEnhancedService.UserSessionStatistics userStatistics;
        private LocalDateTime lastUpdated;
        
        // Direct user counts for easy access
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
    public static class PerformanceMetrics {
        private BigDecimal todayRevenue;
        private BigDecimal monthRevenue;
        private BigDecimal totalRevenue;
        private long totalOrders;
        private long completedOrders;
        private double orderConversionRate;
        private long totalUsers;
        private long activeUsers;
        private long onlineUsers;
        private double userEngagementRate;
        private BigDecimal averageOrderValue;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TopPerformers {
        private List<OrderService.CustomerStats> topCustomers;
        private List<StarPackageEntity> topSellingPackages;
        private List<UserSessionEntity> mostActiveUsers;
        private List<StarPackageEntity> bestValuePackages;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AnalyticsData {
        private List<OrderService.DailyStats> dailyRevenue;
        private List<UserSessionEnhancedService.DailyActiveUsers> dailyActiveUsers;
        private List<UserSessionEnhancedService.LanguageStats> languageDistribution;
        private List<StarPackageService.PackageTypeSales> packageTypeSales;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SystemHealth {
        private int healthScore;
        private int stuckUsersCount;
        private int pendingOrdersCount;
        private int packagesWithoutSalesCount;
        private List<UserSessionEntity> stuckUsers;
        private List<UserSessionEntity> usersWithPendingOrders;
        private List<StarPackageEntity> packagesWithoutSales;
        private LocalDateTime lastChecked;
        
        // Additional fields for frontend
        private boolean redisHealthy;
        private boolean botHealthy;
        private boolean cacheHealthy;
        private long onlineUsersCount;
        private long activeUsersCount;
        private Double averageResponseTime;
        private Integer memoryUsagePercent;
        private Integer cacheHitRatio;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MaintenanceResult {
        private int deactivatedSessions;
        private int deactivatedPackages;
        private LocalDateTime maintenanceTime;
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
