package shit.back.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shit.back.entity.OrderEntity;
import shit.back.entity.OrderEntity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {

    // Поиск по пользователю
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Поиск по статусу
    List<OrderEntity> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    Page<OrderEntity> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    // Поиск по пользователю и статусу
    List<OrderEntity> findByUserIdAndStatus(Long userId, OrderStatus status);

    Optional<OrderEntity> findByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);

    // Поиск активных заказов пользователя
    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.status IN ('CREATED', 'AWAITING_PAYMENT', 'PAYMENT_RECEIVED', 'PROCESSING')")
    List<OrderEntity> findActiveOrdersByUserId(@Param("userId") Long userId);

    // Статистика по заказам
    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.status = :status")
    Long countByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.createdAt >= :fromDate")
    Long countOrdersSince(@Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT SUM(o.finalAmount) FROM OrderEntity o WHERE o.status = 'COMPLETED'")
    BigDecimal getTotalRevenue();

    @Query("SELECT SUM(o.finalAmount) FROM OrderEntity o WHERE o.status = 'COMPLETED' AND o.completedAt >= :fromDate")
    BigDecimal getRevenueSince(@Param("fromDate") LocalDateTime fromDate);

    // Статистика по пакетам
    @Query("SELECT o.starPackageName, COUNT(o), SUM(o.finalAmount) FROM OrderEntity o WHERE o.status = 'COMPLETED' GROUP BY o.starPackageName ORDER BY COUNT(o) DESC")
    List<Object[]> getPackageStatistics();

    // Поиск по временному периоду
    List<OrderEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    @Query("SELECT o FROM OrderEntity o WHERE o.createdAt >= :fromDate ORDER BY o.createdAt DESC")
    List<OrderEntity> findRecentOrders(@Param("fromDate") LocalDateTime fromDate);

    // Поиск по username или orderId
    @Query("SELECT o FROM OrderEntity o WHERE o.username LIKE %:searchTerm% OR o.orderId LIKE %:searchTerm%")
    Page<OrderEntity> searchOrders(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Топ покупатели
    @Query("SELECT o.userId, o.username, COUNT(o), SUM(o.finalAmount) FROM OrderEntity o WHERE o.status = 'COMPLETED' GROUP BY o.userId, o.username ORDER BY COUNT(o) DESC")
    List<Object[]> getTopCustomers(Pageable pageable);

    // Заказы за сегодня
    @Query("SELECT o FROM OrderEntity o WHERE o.createdAt >= :startOfDay AND o.createdAt < :endOfDay ORDER BY o.createdAt DESC")
    List<OrderEntity> getTodaysOrders(@Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    // Незавершенные заказы старше определенного времени
    @Query("SELECT o FROM OrderEntity o WHERE o.status IN ('CREATED', 'AWAITING_PAYMENT') AND o.createdAt < :cutoffTime")
    List<OrderEntity> findStaleOrders(@Param("cutoffTime") LocalDateTime cutoffTime);

    // Статистика по дням
    @Query("SELECT FUNCTION('DATE', o.createdAt), COUNT(o), SUM(o.finalAmount) FROM OrderEntity o WHERE o.createdAt >= :fromDate GROUP BY FUNCTION('DATE', o.createdAt) ORDER BY FUNCTION('DATE', o.createdAt)")
    List<Object[]> getDailyStatistics(@Param("fromDate") LocalDateTime fromDate);

    // Подсчет заказов пользователя
    Long countByUserId(Long userId);

    // Проверка существования активного заказа
    boolean existsByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);

    // Последний заказ пользователя
    Optional<OrderEntity> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    // Заказы по методу платежа
    List<OrderEntity> findByPaymentMethodOrderByCreatedAtDesc(String paymentMethod);

    // Средний чек
    @Query("SELECT AVG(o.finalAmount) FROM OrderEntity o WHERE o.status = 'COMPLETED'")
    BigDecimal getAverageOrderValue();

    // Конверсия заказов
    @Query("SELECT " +
            "SUM(CASE WHEN o.status = 'COMPLETED' THEN 1 ELSE 0 END) * 100.0 / COUNT(o) " +
            "FROM OrderEntity o")
    Double getOrderConversionRate();

    /**
     * КРИТИЧЕСКАЯ ОПТИМИЗАЦИЯ: Объединяет все статистические запросы в ОДИН SQL
     * запрос
     * Заменяет 9+ отдельных запросов на одну операцию с CASE WHEN агрегацией
     * Значительно улучшает производительность Orders/Dashboard страниц
     */
    @Query(value = """
            SELECT
                -- Общее количество заказов
                COUNT(*) as total_orders,

                -- Завершенные заказы
                SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_orders,

                -- Ожидающие заказы (CREATED + AWAITING_PAYMENT)
                SUM(CASE WHEN status IN ('CREATED', 'AWAITING_PAYMENT') THEN 1 ELSE 0 END) as pending_orders,

                -- Неудачные заказы
                SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_orders,

                -- Общая выручка (только завершенные)
                COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN final_amount ELSE 0 END), 0) as total_revenue,

                -- Выручка за сегодня
                COALESCE(SUM(CASE
                    WHEN status = 'COMPLETED' AND DATE(created_at) = CURRENT_DATE
                    THEN final_amount ELSE 0 END), 0) as today_revenue,

                -- Выручка за текущий месяц
                COALESCE(SUM(CASE
                    WHEN status = 'COMPLETED'
                        AND EXTRACT(YEAR FROM created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
                        AND EXTRACT(MONTH FROM created_at) = EXTRACT(MONTH FROM CURRENT_DATE)
                    THEN final_amount ELSE 0 END), 0) as month_revenue,

                -- Средний чек (только завершенные)
                COALESCE(AVG(CASE WHEN status = 'COMPLETED' THEN final_amount END), 0) as average_order_value

            FROM orders
            """, nativeQuery = true)
    List<Object[]> getOrderStatisticsOptimized();
}
