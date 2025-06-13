package shit.back.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shit.back.entity.PaymentEntity;
import shit.back.entity.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA репозиторий для работы с платежами
 */
@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    /**
     * Найти платеж по уникальному ID платежа
     */
    Optional<PaymentEntity> findByPaymentId(String paymentId);

    /**
     * Найти платеж по внешнему ID платежной системы
     */
    Optional<PaymentEntity> findByExternalPaymentId(String externalPaymentId);

    /**
     * Найти платежи пользователя
     */
    List<PaymentEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Найти платежи пользователя с пагинацией
     */
    Page<PaymentEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Найти платежи по статусу
     */
    List<PaymentEntity> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    /**
     * Найти платежи пользователя по статусу
     */
    List<PaymentEntity> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PaymentStatus status);

    /**
     * Найти платежи по способу оплаты
     */
    List<PaymentEntity> findByPaymentMethodOrderByCreatedAtDesc(String paymentMethod);

    /**
     * Найти платежи пользователя по способу оплаты
     */
    List<PaymentEntity> findByUserIdAndPaymentMethodOrderByCreatedAtDesc(Long userId, String paymentMethod);

    /**
     * Найти платежи по заказу
     */
    List<PaymentEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);

    /**
     * Найти последний платеж пользователя
     */
    Optional<PaymentEntity> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Найти последний успешный платеж пользователя
     */
    Optional<PaymentEntity> findTopByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PaymentStatus status);

    /**
     * Найти истекшие платежи, которые нужно обновить
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.expiresAt < :currentTime AND p.status IN :activeStatuses")
    List<PaymentEntity> findExpiredPayments(@Param("currentTime") LocalDateTime currentTime,
            @Param("activeStatuses") List<PaymentStatus> activeStatuses);

    /**
     * Найти платежи, требующие обработки
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.status IN :processingStatuses AND p.retryCount < :maxRetries")
    List<PaymentEntity> findPaymentsRequiringProcessing(
            @Param("processingStatuses") List<PaymentStatus> processingStatuses,
            @Param("maxRetries") Integer maxRetries);

    /**
     * Найти платежи за период
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.createdAt BETWEEN :startDate AND :endDate ORDER BY p.createdAt DESC")
    List<PaymentEntity> findPaymentsBetweenDates(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Найти платежи пользователя за период
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.userId = :userId AND p.createdAt BETWEEN :startDate AND :endDate ORDER BY p.createdAt DESC")
    List<PaymentEntity> findUserPaymentsBetweenDates(@Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Подсчитать общее количество платежей пользователя
     */
    long countByUserId(Long userId);

    /**
     * Подсчитать количество успешных платежей пользователя
     */
    long countByUserIdAndStatus(Long userId, PaymentStatus status);

    /**
     * Подсчитать платежи по способу оплаты
     */
    long countByPaymentMethod(String paymentMethod);

    /**
     * Найти платежи с определенным количеством попыток
     */
    List<PaymentEntity> findByRetryCountGreaterThanEqual(Integer retryCount);

    /**
     * Статистика платежей за период по статусам
     */
    @Query("SELECT p.status, COUNT(p) FROM PaymentEntity p WHERE p.createdAt BETWEEN :startDate AND :endDate GROUP BY p.status")
    List<Object[]> getPaymentStatisticsByStatus(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Статистика платежей за период по методам оплаты
     */
    @Query("SELECT p.paymentMethod, COUNT(p) FROM PaymentEntity p WHERE p.createdAt BETWEEN :startDate AND :endDate GROUP BY p.paymentMethod")
    List<Object[]> getPaymentStatisticsByMethod(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Найти дублирующиеся платежи по внешнему ID
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.externalPaymentId = :externalId AND p.id != :excludeId")
    List<PaymentEntity> findDuplicatesByExternalId(@Param("externalId") String externalId,
            @Param("excludeId") Long excludeId);

    /**
     * Найти платежи для автоматической отмены (истекшие и в ожидании)
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.expiresAt < :currentTime AND p.status = 'PENDING'")
    List<PaymentEntity> findPaymentsToAutoCancel(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Найти последние N платежей пользователя
     */
    List<PaymentEntity> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Проверить существование активного платежа для заказа
     */
    @Query("SELECT COUNT(p) > 0 FROM PaymentEntity p WHERE p.orderId = :orderId AND p.status IN :activeStatuses")
    boolean existsActivePaymentForOrder(@Param("orderId") String orderId,
            @Param("activeStatuses") List<PaymentStatus> activeStatuses);

    /**
     * Найти все платежи пользователя по методу оплаты за последние N дней
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.userId = :userId AND p.paymentMethod = :method AND p.createdAt >= :sinceDate ORDER BY p.createdAt DESC")
    List<PaymentEntity> findRecentUserPaymentsByMethod(@Param("userId") Long userId,
            @Param("method") String method,
            @Param("sinceDate") LocalDateTime sinceDate);
}