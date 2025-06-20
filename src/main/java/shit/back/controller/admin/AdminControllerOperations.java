package shit.back.controller.admin;

import org.springframework.http.ResponseEntity;
import shit.back.service.AdminDashboardService;
import shit.back.service.AdminDashboardCacheService;
import shit.back.service.UserActivityLogService;

import java.time.LocalDateTime;
import java.util.Map;

import shit.back.dto.monitoring.SystemHealth;
import shit.back.dto.monitoring.PerformanceMetrics;

/**
 * Базовый интерфейс для операций админ панели
 * Определяет общие методы для UI и API контроллеров
 * Создан для устранения дублирования между AdminController и
 * OptimizedAdminController
 */
public interface AdminControllerOperations {

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
    UserActivityLogService.ActivityStatistics getActivityStatistics(int hours);

    /**
     * Получение статуса платежей
     */
    UserActivityLogService.PaymentStatusDashboard getPaymentStatusDashboard();

    /**
     * Получение полных данных dashboard (кэшированных)
     */
    AdminDashboardCacheService.FullDashboardDataCached getFullDashboardData();
}