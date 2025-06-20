package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.StarPackageEntity;
import shit.back.entity.UserSessionEntity;
import shit.back.model.UserCountsBatchResult;
import shit.back.dto.order.DailyStats;
import shit.back.dto.order.CustomerStats;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис аналитики для админской панели
 * Выделен из AdminDashboardService в рамках разделения God Classes (Week 3-4)
 * 
 * Отвечает за:
 * - Метрики производительности (revenue, conversion rates)
 * - Аналитические данные для графиков
 * - Топ-исполнители (customers, packages, users)
 * - Статистические расчеты
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring - God Class Split
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AdminAnalyticsService {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StarPackageService starPackageService;

    @Autowired
    private UserSessionUnifiedService userSessionService;

    /**
     * Получение метрик производительности системы
     * ОПТИМИЗИРОВАНО: использует batch query для пользовательских метрик
     */
    public shit.back.dto.monitoring.PerformanceMetrics getPerformanceMetrics() {
        long startTime = System.currentTimeMillis();
        log.info("📊 АНАЛИТИКА: Расчет метрик производительности - НАЧАЛО");

        // Revenue metrics
        long revenueStart = System.currentTimeMillis();
        BigDecimal todayRevenue = orderService.getTodayRevenue();
        BigDecimal monthRevenue = orderService.getMonthRevenue();
        BigDecimal totalRevenue = orderService.getTotalRevenue();
        long revenueTime = System.currentTimeMillis() - revenueStart;
        log.info("💰 АНАЛИТИКА: Revenue metrics took {}ms", revenueTime);

        // Conversion metrics
        long conversionStart = System.currentTimeMillis();
        long totalOrders = orderService.getTotalOrdersCount();
        long completedOrders = orderService.getCompletedOrdersCount();
        double orderConversionRate = totalOrders > 0 ? (double) completedOrders / totalOrders * 100 : 0;
        long conversionTime = System.currentTimeMillis() - conversionStart;
        log.info("📈 АНАЛИТИКА: Conversion metrics took {}ms", conversionTime);

        // User engagement metrics - OPTIMIZED WITH BATCH QUERY
        long engagementStart = System.currentTimeMillis();
        UserCountsBatchResult engagementCounts = userSessionService.getUserCountsBatch();
        long totalUsers = engagementCounts.totalUsers();
        long activeUsers = engagementCounts.activeUsers();
        long onlineUsers = engagementCounts.onlineUsers();
        double userEngagementRate = totalUsers > 0 ? (double) activeUsers / totalUsers * 100 : 0;
        long engagementTime = System.currentTimeMillis() - engagementStart;
        log.info("✅ АНАЛИТИКА ОПТИМИЗИРОВАНА: User engagement metrics took {}ms - SINGLE BATCH QUERY!",
                engagementTime);

        long avgOrderStart = System.currentTimeMillis();
        BigDecimal avgOrderValue = orderService.getAverageOrderValue();
        long avgOrderTime = System.currentTimeMillis() - avgOrderStart;
        log.info("💵 АНАЛИТИКА: Average order value took {}ms", avgOrderTime);

        long totalTime = System.currentTimeMillis() - startTime;
        log.warn(
                "📊 АНАЛИТИКА ЗАВЕРШЕНА: Performance metrics TOTAL time {}ms (revenue:{}ms, conversion:{}ms, engagement:{}ms, avg:{}ms)",
                totalTime, revenueTime, conversionTime, engagementTime, avgOrderTime);

        // Маппинг бизнесовых метрик в технические (заглушка, т.к. структура классов не
        // совпадает)
        // Здесь можно реализовать преобразование или вернуть фиктивные значения, если
        // нет данных
        return new shit.back.dto.monitoring.PerformanceMetrics(
                0.0, // cpuUsage
                0.0, // memoryUsage
                0.0, // responseTime
                totalOrders, // requestCount
                0, // errorCount
                0 // uptime
        );
    }

    /**
     * Получение топ-исполнителей системы
     */
    public TopPerformers getTopPerformers() {
        long startTime = System.currentTimeMillis();
        log.info("🏆 АНАЛИТИКА: Получение топ-исполнителей - НАЧАЛО");

        List<CustomerStats> topCustomers = orderService.getTopCustomers(10);
        List<StarPackageEntity> topPackages = starPackageService.getTopSellingPackages(10);
        List<UserSessionEntity> topActiveUsers = userSessionService.getTopActiveUsers(10);
        List<StarPackageEntity> bestValuePackages = starPackageService.getBestValuePackages(5);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("🏆 АНАЛИТИКА: Топ-исполнители получены за {}ms", totalTime);

        return TopPerformers.builder()
                .topCustomers(topCustomers)
                .topSellingPackages(topPackages)
                .mostActiveUsers(topActiveUsers)
                .bestValuePackages(bestValuePackages)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Получение аналитических данных для графиков
     */
    public AnalyticsData getAnalyticsData(int days) {
        long startTime = System.currentTimeMillis();
        log.info("📈 АНАЛИТИКА: Получение данных для графиков за {} дней - НАЧАЛО", days);

        List<DailyStats> dailyRevenue = orderService.getDailyStatistics(days);

        // Временно заглушка для данных активных пользователей - будет добавлена позже
        List<DailyActiveUsersStats> dailyActiveUsers = List.of();

        // Временно заглушка для языковой статистики - будет добавлена позже
        List<LanguageStats> languageStats = List.of();

        List<StarPackageService.PackageTypeSales> packageTypeSales = starPackageService.getSalesByPackageType();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("📈 АНАЛИТИКА: Данные для графиков получены за {}ms", totalTime);

        return AnalyticsData.builder()
                .dailyRevenue(dailyRevenue)
                .dailyActiveUsers(dailyActiveUsers)
                .languageDistribution(languageStats)
                .packageTypeSales(packageTypeSales)
                .periodDays(days)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Расчет KPI (Key Performance Indicators) системы
     */
    public SystemKPI calculateSystemKPI() {
        log.info("📋 АНАЛИТИКА: Расчет системных KPI");

        shit.back.dto.monitoring.PerformanceMetrics metrics = getPerformanceMetrics();
        TopPerformers performers = getTopPerformers();

        // Расчет дополнительных KPI
        double revenueGrowthRate = calculateRevenueGrowthRate();
        double userRetentionRate = calculateUserRetentionRate();
        double averageSessionDuration = calculateAverageSessionDuration();

        return SystemKPI.builder()
                .revenueGrowthRate(revenueGrowthRate)
                .userRetentionRate(userRetentionRate)
                // .orderConversionRate(metrics.getOrderConversionRate())
                // .userEngagementRate(metrics.getUserEngagementRate())
                // .averageOrderValue(metrics.getAverageOrderValue())
                .averageSessionDuration(averageSessionDuration)
                .topCustomersCount(performers.getTopCustomers().size())
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    // Приватные методы для расчета дополнительных метрик

    private double calculateRevenueGrowthRate() {
        // Заглушка - реальная логика будет добавлена позже
        return 15.5; // 15.5% рост
    }

    private double calculateUserRetentionRate() {
        // Заглушка - реальная логика будет добавлена позже
        return 78.3; // 78.3% retention
    }

    private double calculateAverageSessionDuration() {
        // Заглушка - реальная логика будет добавлена позже
        return 24.7; // 24.7 минут средняя сессия
    }

    // Data Transfer Objects

    // PerformanceMetrics DTO теперь используется внешний:
    // shit.back.dto.monitoring.PerformanceMetrics

    @lombok.Data
    @lombok.Builder
    public static class TopPerformers {
        private List<CustomerStats> topCustomers;
        private List<StarPackageEntity> topSellingPackages;
        private List<UserSessionEntity> mostActiveUsers;
        private List<StarPackageEntity> bestValuePackages;
        private LocalDateTime generatedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class AnalyticsData {
        private List<DailyStats> dailyRevenue;
        private List<DailyActiveUsersStats> dailyActiveUsers;
        private List<LanguageStats> languageDistribution;
        private List<StarPackageService.PackageTypeSales> packageTypeSales;
        private int periodDays;
        private LocalDateTime generatedAt;
    }

    // Вспомогательные DTO классы для аналитики
    @lombok.Data
    @lombok.Builder
    public static class DailyActiveUsersStats {
        private LocalDateTime date;
        private long activeUsers;
        private long newUsers;
        private long returningUsers;
    }

    @lombok.Data
    @lombok.Builder
    public static class LanguageStats {
        private String language;
        private long userCount;
        private double percentage;
    }

    @lombok.Data
    @lombok.Builder
    public static class SystemKPI {
        private double revenueGrowthRate;
        private double userRetentionRate;
        private double orderConversionRate;
        private double userEngagementRate;
        private BigDecimal averageOrderValue;
        private double averageSessionDuration;
        private int topCustomersCount;
        private LocalDateTime calculatedAt;
    }
}