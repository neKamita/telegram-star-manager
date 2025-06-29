package shit.back.web.controller.admin;

import shit.back.dto.monitoring.PerformanceMetrics;
import shit.back.dto.monitoring.SystemHealth;
import shit.back.service.AdminDashboardCacheService;
import shit.back.service.AdminDashboardService;
import shit.back.service.UserActivityLogService;
import shit.back.service.activity.UserActivityStatisticsService;

import java.util.Map;

/**
 * Интерфейс для операций админ dashboard'а
 * Определяет общие методы для UI и API контроллеров
 * Следует принципу Interface Segregation (ISP) из SOLID
 */
public interface AdminDashboardOperations {

    /**
     * Получение данных dashboard
     */
    AdminDashboardService.DashboardOverview getDashboardOverview();

    /**
     * Получение системного здоровья
     */
    SystemHealth getSystemHealth();

    /**
     * Получение недавней активности
     */
    AdminDashboardService.RecentActivity getRecentActivity();

    /**
     * Получение метрик производительности
     */
    PerformanceMetrics getPerformanceMetrics();

    /**
     * Обновление кэша
     */
    Map<String, Object> refreshCache();

    /**
     * Получение быстрых статистик
     */
    Map<String, Object> getQuickStats();

    /**
     * Получение статистики активности
     */
    UserActivityStatisticsService.ActivityStatistics getActivityStatistics(int hours);

    /**
     * Получение статуса платежей
     */
    UserActivityStatisticsService.PaymentStatusDashboard getPaymentStatusDashboard();

    /**
     * Получение полных данных dashboard (кэшированных)
     */
    AdminDashboardCacheService.FullDashboardDataCached getFullDashboardData();
}