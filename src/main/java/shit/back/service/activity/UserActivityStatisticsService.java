package shit.back.service.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.UserActivityLogEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.entity.UserActivityLogEntity.LogCategory;
import shit.back.repository.UserActivityLogJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сервис для работы со статистикой и аналитикой активности пользователей
 * РЕФАКТОРИНГ: Выделен из UserActivityLogService для соблюдения SRP
 * 
 * Отвечает только за:
 * - Получение данных активности
 * - Расчет статистики
 * - Аналитические операции
 * - Конвертацию данных
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class UserActivityStatisticsService {

    @Autowired
    private UserActivityLogJpaRepository activityLogRepository;

    // ==================== МЕТОДЫ ПОЛУЧЕНИЯ ДАННЫХ ====================

    /**
     * Получить активности с фильтрами - временно используем простой метод
     */
    public Page<UserActivityLogEntity> getActivitiesWithFilters(
            boolean showAll,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            List<ActionType> actionTypes,
            String searchTerm,
            Pageable pageable) {

        // ВРЕМЕННОЕ РЕШЕНИЕ: используем только ключевые активности если showAll=false
        if (!showAll) {
            return activityLogRepository.findKeyActionsOrderByTimestampDesc(pageable);
        } else {
            return activityLogRepository.findAllByOrderByTimestampDesc(pageable);
        }
    }

    /**
     * Получить только ключевые активности
     */
    public Page<UserActivityLogEntity> getKeyActivities(Pageable pageable) {
        return activityLogRepository.findKeyActionsOrderByTimestampDesc(pageable);
    }

    /**
     * Получить все активности
     */
    public Page<UserActivityLogEntity> getAllActivities(Pageable pageable) {
        return activityLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    /**
     * Получить активности пользователя
     */
    public Page<UserActivityLogEntity> getUserActivities(Long userId, Pageable pageable) {
        return activityLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    /**
     * Получить активности по заказу
     */
    public List<UserActivityLogEntity> getOrderActivities(String orderId) {
        return activityLogRepository.findByOrderIdOrderByTimestampDesc(orderId);
    }

    /**
     * Получить последние активности для live feed
     */
    public List<UserActivityLogEntity> getRecentActivities(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return activityLogRepository.findRecentKeyActivities(since);
    }

    // ==================== МЕТОДЫ ПОЛУЧЕНИЯ ДАННЫХ ПО КАТЕГОРИЯМ
    // ====================

    /**
     * Получить активности по категории
     */
    public Page<UserActivityLogEntity> getActivitiesByCategory(LogCategory logCategory, Pageable pageable) {
        return activityLogRepository.findByLogCategoryOrderByTimestampDesc(logCategory, pageable);
    }

    /**
     * Получить активности телеграм бота
     */
    public Page<UserActivityLogEntity> getTelegramBotActivities(int hours, Pageable pageable) {
        LocalDateTime fromTime = LocalDateTime.now().minusHours(hours);
        return activityLogRepository.findTelegramBotActivities(fromTime, pageable);
    }

    /**
     * Получить активности приложения
     */
    public Page<UserActivityLogEntity> getApplicationActivities(int hours, Pageable pageable) {
        LocalDateTime fromTime = LocalDateTime.now().minusHours(hours);
        return activityLogRepository.findApplicationActivities(fromTime, pageable);
    }

    /**
     * Получить активности с расширенными фильтрами - временно упрощено
     */
    public Page<UserActivityLogEntity> getActivitiesWithCategoryFilters(
            boolean showAll,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            List<ActionType> actionTypes,
            List<LogCategory> logCategories,
            String searchTerm,
            Pageable pageable) {

        // ВРЕМЕННОЕ РЕШЕНИЕ: используем только ключевые активности если showAll=false
        if (!showAll) {
            return activityLogRepository.findKeyActionsOrderByTimestampDesc(pageable);
        } else {
            return activityLogRepository.findAllByOrderByTimestampDesc(pageable);
        }
    }

    // ==================== СТАТИСТИКА И АНАЛИТИКА ====================

    /**
     * Получить статистику активности
     */
    public ActivityStatistics getActivityStatistics(int hours) {
        LocalDateTime fromTime = LocalDateTime.now().minusHours(hours);

        long totalActivities = activityLogRepository.countByTimestampAfter(fromTime);
        long keyActivities = activityLogRepository.countByIsKeyActionTrueAndTimestampAfter(fromTime);

        List<Object[]> actionStats = activityLogRepository.getKeyActionTypeStatistics(fromTime);
        List<Object[]> userStats = activityLogRepository.getMostActiveUsers(fromTime);
        List<Object[]> paymentStats = activityLogRepository.getPaymentMethodStatistics(fromTime);

        return ActivityStatistics.builder()
                .totalActivities(totalActivities)
                .keyActivities(keyActivities)
                .actionTypeStats(convertActionTypeStats(actionStats))
                .mostActiveUsers(convertUserStats(userStats))
                .paymentMethodStats(convertPaymentStats(paymentStats))
                .periodHours(hours)
                .build();
    }

    /**
     * Получить статистику по категориям логов
     */
    public CategoryStatistics getCategoryStatistics(int hours) {
        LocalDateTime fromTime = LocalDateTime.now().minusHours(hours);

        long telegramBotActivities = activityLogRepository.countByLogCategoryAndTimestampAfter(LogCategory.TELEGRAM_BOT,
                fromTime);
        long applicationActivities = activityLogRepository.countByLogCategoryAndTimestampAfter(LogCategory.APPLICATION,
                fromTime);
        long systemActivities = activityLogRepository.countByLogCategoryAndTimestampAfter(LogCategory.SYSTEM, fromTime);

        // Проверяем общее количество логов для валидации
        long totalLogsInPeriod = activityLogRepository.countByTimestampAfter(fromTime);

        log.debug("CategoryStatistics: period={}h, total={}, telegram={}, app={}, system={}",
                hours, totalLogsInPeriod, telegramBotActivities, applicationActivities, systemActivities);

        long telegramBotKeyActivities = activityLogRepository
                .countByLogCategoryAndIsKeyActionTrueAndTimestampAfter(LogCategory.TELEGRAM_BOT, fromTime);
        long applicationKeyActivities = activityLogRepository
                .countByLogCategoryAndIsKeyActionTrueAndTimestampAfter(LogCategory.APPLICATION, fromTime);
        long systemKeyActivities = activityLogRepository
                .countByLogCategoryAndIsKeyActionTrueAndTimestampAfter(LogCategory.SYSTEM, fromTime);

        List<Object[]> categoryStats = activityLogRepository.getLogCategoryStatistics(fromTime);
        List<Object[]> categoryActionStats = activityLogRepository.getCategoryActionTypeStatistics(fromTime);

        return CategoryStatistics.builder()
                .telegramBotActivities(telegramBotActivities)
                .applicationActivities(applicationActivities)
                .systemActivities(systemActivities)
                .telegramBotKeyActivities(telegramBotKeyActivities)
                .applicationKeyActivities(applicationKeyActivities)
                .systemKeyActivities(systemKeyActivities)
                .categoryStats(convertCategoryStats(categoryStats))
                .categoryActionStats(convertCategoryActionStats(categoryActionStats))
                .periodHours(hours)
                .build();
    }

    /**
     * Получить dashboard для статусов оплаты
     */
    public PaymentStatusDashboard getPaymentStatusDashboard() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        // Получить активности по оплатам за сегодня
        List<UserActivityLogEntity> completedPayments = activityLogRepository
                .findByActionTypeOrderByTimestampDesc(ActionType.PAYMENT_COMPLETED)
                .stream()
                .filter(a -> a.getTimestamp().isAfter(today))
                .collect(Collectors.toList());

        List<UserActivityLogEntity> pendingPayments = activityLogRepository
                .findByActionTypeOrderByTimestampDesc(ActionType.PAYMENT_INITIATED)
                .stream()
                .filter(a -> a.getTimestamp().isAfter(today))
                .collect(Collectors.toList());

        List<UserActivityLogEntity> failedPayments = activityLogRepository
                .findByActionTypeOrderByTimestampDesc(ActionType.PAYMENT_FAILED)
                .stream()
                .filter(a -> a.getTimestamp().isAfter(today))
                .collect(Collectors.toList());

        List<UserActivityLogEntity> cancelledOrders = activityLogRepository
                .findByActionTypeOrderByTimestampDesc(ActionType.ORDER_CANCELLED)
                .stream()
                .filter(a -> a.getTimestamp().isAfter(today))
                .collect(Collectors.toList());

        // Получить пользователей, зависших в оплате (более 30 минут)
        LocalDateTime stuckCutoff = LocalDateTime.now().minusMinutes(30);
        List<Object[]> stuckUsers = activityLogRepository.findUsersStuckInPayment(stuckCutoff);

        // Подсчитать статистику
        BigDecimal totalSalesAmount = completedPayments.stream()
                .filter(a -> a.getOrderAmount() != null)
                .map(UserActivityLogEntity::getOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double successRate = 0.0;
        if (!pendingPayments.isEmpty() || !completedPayments.isEmpty()) {
            int totalAttempts = completedPayments.size() + failedPayments.size();
            if (totalAttempts > 0) {
                successRate = (double) completedPayments.size() / totalAttempts * 100.0;
            }
        }

        return PaymentStatusDashboard.builder()
                .completedPayments(completedPayments)
                .pendingPayments(pendingPayments)
                .failedPayments(failedPayments)
                .cancelledOrders(cancelledOrders)
                .stuckUsers(convertStuckUsers(stuckUsers))
                .totalSalesAmount(totalSalesAmount)
                .averageOrderAmount(calculateAverageOrderAmount(completedPayments))
                .successRate(successRate)
                .mostPopularPackage(findMostPopularPackage(completedPayments))
                .build();
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ КОНВЕРТАЦИИ ====================

    private List<ActionTypeStat> convertActionTypeStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new ActionTypeStat((ActionType) row[0], (Long) row[1]))
                .collect(Collectors.toList());
    }

    private List<UserActivityStat> convertUserStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new UserActivityStat((Long) row[0], (String) row[1], (Long) row[2]))
                .collect(Collectors.toList());
    }

    private List<PaymentMethodStat> convertPaymentStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new PaymentMethodStat(
                        (String) row[0],
                        (Long) row[1],
                        (BigDecimal) row[2]))
                .collect(Collectors.toList());
    }

    private List<StuckUser> convertStuckUsers(List<Object[]> stuckUsers) {
        return stuckUsers.stream()
                .map(row -> new StuckUser(
                        (Long) row[0],
                        (String) row[1],
                        (LocalDateTime) row[2]))
                .collect(Collectors.toList());
    }

    private BigDecimal calculateAverageOrderAmount(List<UserActivityLogEntity> completedPayments) {
        if (completedPayments.isEmpty())
            return BigDecimal.ZERO;

        List<BigDecimal> amounts = completedPayments.stream()
                .map(UserActivityLogEntity::getOrderAmount)
                .filter(Objects::nonNull)
                .toList();

        if (amounts.isEmpty())
            return BigDecimal.ZERO;

        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(amounts.size()), 2, BigDecimal.ROUND_HALF_UP);
    }

    private String findMostPopularPackage(List<UserActivityLogEntity> completedPayments) {
        return completedPayments.stream()
                .filter(a -> a.getStarCount() != null)
                .collect(Collectors.groupingBy(
                        UserActivityLogEntity::getStarCount,
                        Collectors.counting()))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + "⭐ Package")
                .orElse("N/A");
    }

    private List<CategoryStat> convertCategoryStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new CategoryStat((LogCategory) row[0], (Long) row[1]))
                .collect(Collectors.toList());
    }

    private List<CategoryActionStat> convertCategoryActionStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new CategoryActionStat(
                        (LogCategory) row[0],
                        (ActionType) row[1],
                        (Long) row[2]))
                .collect(Collectors.toList());
    }

    // ==================== DATA TRANSFER OBJECTS ====================

    @lombok.Data
    @lombok.Builder
    public static class ActivityStatistics {
        private long totalActivities;
        private long keyActivities;
        private List<ActionTypeStat> actionTypeStats;
        private List<UserActivityStat> mostActiveUsers;
        private List<PaymentMethodStat> paymentMethodStats;
        private int periodHours;
    }

    @lombok.Data
    @lombok.Builder
    public static class PaymentStatusDashboard {
        private List<UserActivityLogEntity> completedPayments;
        private List<UserActivityLogEntity> pendingPayments;
        private List<UserActivityLogEntity> failedPayments;
        private List<UserActivityLogEntity> cancelledOrders;
        private List<StuckUser> stuckUsers;
        private BigDecimal totalSalesAmount;
        private BigDecimal averageOrderAmount;
        private double successRate;
        private String mostPopularPackage;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ActionTypeStat {
        private ActionType actionType;
        private Long count;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class UserActivityStat {
        private Long userId;
        private String username;
        private Long activityCount;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PaymentMethodStat {
        private String paymentMethod;
        private Long count;
        private BigDecimal totalAmount;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StuckUser {
        private Long userId;
        private String username;
        private LocalDateTime lastActivity;
    }

    @lombok.Data
    @lombok.Builder
    public static class CategoryStatistics {
        private long telegramBotActivities;
        private long applicationActivities;
        private long systemActivities;
        private long telegramBotKeyActivities;
        private long applicationKeyActivities;
        private long systemKeyActivities;
        private List<CategoryStat> categoryStats;
        private List<CategoryActionStat> categoryActionStats;
        private int periodHours;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CategoryStat {
        private LogCategory logCategory;
        private Long count;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CategoryActionStat {
        private LogCategory logCategory;
        private ActionType actionType;
        private Long count;
    }
}