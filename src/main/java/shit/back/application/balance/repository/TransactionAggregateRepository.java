package shit.back.application.balance.repository;

import shit.back.domain.balance.TransactionAggregate;
import shit.back.domain.balance.valueobjects.TransactionId;
import shit.back.entity.TransactionStatus;
import shit.back.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface для TransactionAggregate
 * Определяет контракт для работы с доменными агрегатами транзакций
 * Реализуется в Infrastructure Layer
 */
public interface TransactionAggregateRepository {

    /**
     * Сохраняет агрегат транзакции
     * 
     * @param transaction агрегат для сохранения
     * @return сохраненный агрегат
     */
    TransactionAggregate save(TransactionAggregate transaction);

    /**
     * Находит агрегат транзакции по ID
     * 
     * @param transactionId ID транзакции
     * @return Optional с агрегатом или пустой
     */
    Optional<TransactionAggregate> findById(TransactionId transactionId);

    /**
     * Находит агрегат транзакции по строковому ID
     * 
     * @param transactionId строковый ID транзакции
     * @return Optional с агрегатом или пустой
     */
    Optional<TransactionAggregate> findByTransactionId(String transactionId);

    /**
     * Находит все транзакции пользователя
     * 
     * @param userId ID пользователя
     * @return список агрегатов транзакций
     */
    List<TransactionAggregate> findByUserId(Long userId);

    /**
     * Находит транзакции пользователя по статусу
     * 
     * @param userId ID пользователя
     * @param status статус транзакции
     * @return список агрегатов
     */
    List<TransactionAggregate> findByUserIdAndStatus(Long userId, TransactionStatus status);

    /**
     * Находит транзакции пользователя по типу
     * 
     * @param userId ID пользователя
     * @param type   тип транзакции
     * @return список агрегатов
     */
    List<TransactionAggregate> findByUserIdAndType(Long userId, TransactionType type);

    /**
     * Находит транзакции в определенном диапазоне дат
     * 
     * @param userId    ID пользователя
     * @param startDate начальная дата
     * @param endDate   конечная дата
     * @return список агрегатов
     */
    List<TransactionAggregate> findByUserIdAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Находит транзакции по ID заказа
     * 
     * @param orderId ID заказа
     * @return список агрегатов
     */
    List<TransactionAggregate> findByOrderId(String orderId);

    /**
     * Находит незавершенные транзакции пользователя
     * 
     * @param userId ID пользователя
     * @return список агрегатов
     */
    List<TransactionAggregate> findPendingTransactionsByUserId(Long userId);

    /**
     * Находит просроченные транзакции
     * 
     * @param expirationDate дата истечения
     * @return список агрегатов
     */
    List<TransactionAggregate> findExpiredTransactions(LocalDateTime expirationDate);

    /**
     * Находит последние транзакции пользователя
     * 
     * @param userId ID пользователя
     * @param limit  количество записей
     * @return список агрегатов
     */
    List<TransactionAggregate> findRecentTransactions(Long userId, int limit);

    /**
     * Проверяет существование транзакции
     * 
     * @param transactionId ID транзакции
     * @return true если существует
     */
    boolean existsById(TransactionId transactionId);

    /**
     * Проверяет существование транзакции по строковому ID
     * 
     * @param transactionId строковый ID транзакции
     * @return true если существует
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Удаляет агрегат транзакции
     * 
     * @param transactionId ID транзакции для удаления
     */
    void deleteById(TransactionId transactionId);

    /**
     * Подсчитывает транзакции пользователя по статусу
     * 
     * @param userId ID пользователя
     * @param status статус транзакции
     * @return количество транзакций
     */
    long countByUserIdAndStatus(Long userId, TransactionStatus status);

    /**
     * Подсчитывает транзакции пользователя за период
     * 
     * @param userId    ID пользователя
     * @param startDate начальная дата
     * @param endDate   конечная дата
     * @return количество транзакций
     */
    long countByUserIdAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Вычисляет сумму транзакций пользователя по типу
     * 
     * @param userId ID пользователя
     * @param type   тип транзакции
     * @return общая сумма
     */
    BigDecimal sumAmountByUserIdAndType(Long userId, TransactionType type);

    /**
     * Вычисляет сумму транзакций пользователя за период
     * 
     * @param userId    ID пользователя
     * @param startDate начальная дата
     * @param endDate   конечная дата
     * @return общая сумма
     */
    BigDecimal sumAmountByUserIdAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Находит транзакции с суммой больше указанной
     * 
     * @param minAmount минимальная сумма
     * @return список агрегатов
     */
    List<TransactionAggregate> findByAmountGreaterThan(BigDecimal minAmount);

    /**
     * Находит агрегаты с пагинацией
     * 
     * @param userId ID пользователя
     * @param offset смещение
     * @param limit  лимит записей
     * @return список агрегатов
     */
    List<TransactionAggregate> findByUserIdWithPagination(Long userId, int offset, int limit);

    /**
     * Блокирует агрегат для конкурентного обновления
     * 
     * @param transactionId ID транзакции
     * @return Optional с заблокированным агрегатом
     */
    Optional<TransactionAggregate> findByIdWithLock(TransactionId transactionId);

    /**
     * Пакетное сохранение агрегатов
     * 
     * @param transactions список агрегатов для сохранения
     * @return список сохраненных агрегатов
     */
    List<TransactionAggregate> saveAll(List<TransactionAggregate> transactions);

    /**
     * Находит все агрегаты (используется с осторожностью для больших данных)
     * 
     * @return список всех агрегатов
     */
    List<TransactionAggregate> findAll();

    /**
     * Получает средний размер транзакции за период
     * 
     * @param startDate начальная дата
     * @param endDate   конечная дата
     * @return средний размер транзакции
     */
    BigDecimal getAverageTransactionAmount(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Очищает кэш репозитория (если используется)
     */
    void clearCache();
}