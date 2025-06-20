package shit.back.application.balance.repository;

import shit.back.domain.balance.BalanceAggregate;
import shit.back.domain.balance.valueobjects.BalanceId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface для BalanceAggregate
 * Определяет контракт для работы с доменными агрегатами балансов
 * Реализуется в Infrastructure Layer
 */
public interface BalanceAggregateRepository {

    /**
     * Сохраняет агрегат баланса
     * 
     * @param balance агрегат для сохранения
     * @return сохраненный агрегат
     */
    BalanceAggregate save(BalanceAggregate balance);

    /**
     * Находит агрегат баланса по ID
     * 
     * @param balanceId ID баланса
     * @return Optional с агрегатом или пустой
     */
    Optional<BalanceAggregate> findById(BalanceId balanceId);

    /**
     * Находит агрегат баланса по ID пользователя
     * 
     * @param userId ID пользователя
     * @return Optional с агрегатом или пустой
     */
    Optional<BalanceAggregate> findByUserId(Long userId);

    /**
     * Находит все агрегаты балансов по валюте
     * 
     * @param currency код валюты
     * @return список агрегатов
     */
    List<BalanceAggregate> findByCurrency(String currency);

    /**
     * Находит активные балансы пользователя
     * 
     * @param userId ID пользователя
     * @return список активных агрегатов
     */
    List<BalanceAggregate> findActiveByUserId(Long userId);

    /**
     * Проверяет существование баланса
     * 
     * @param balanceId ID баланса
     * @return true если существует
     */
    boolean existsById(BalanceId balanceId);

    /**
     * Проверяет существование баланса пользователя
     * 
     * @param userId ID пользователя
     * @return true если существует
     */
    boolean existsByUserId(Long userId);

    /**
     * Удаляет агрегат баланса
     * 
     * @param balanceId ID баланса для удаления
     */
    void deleteById(BalanceId balanceId);

    /**
     * Находит агрегаты с балансом больше указанной суммы
     * 
     * @param minAmount минимальная сумма
     * @param currency  валюта
     * @return список агрегатов
     */
    List<BalanceAggregate> findWithBalanceGreaterThan(java.math.BigDecimal minAmount, String currency);

    /**
     * Находит агрегаты с нулевым балансом
     * 
     * @return список агрегатов
     */
    List<BalanceAggregate> findWithZeroBalance();

    /**
     * Находит топ балансов по сумме
     * 
     * @param limit количество записей
     * @return список агрегатов
     */
    List<BalanceAggregate> findTopByBalance(int limit);

    /**
     * Подсчитывает общее количество активных балансов
     * 
     * @return количество активных балансов
     */
    long countActiveBalances();

    /**
     * Подсчитывает общее количество балансов по валюте
     * 
     * @param currency код валюты
     * @return количество балансов
     */
    long countByCurrency(String currency);

    /**
     * Находит все агрегаты (используется с осторожностью для больших данных)
     * 
     * @return список всех агрегатов
     */
    List<BalanceAggregate> findAll();

    /**
     * Находит агрегаты с пагинацией
     * 
     * @param offset смещение
     * @param limit  лимит записей
     * @return список агрегатов
     */
    List<BalanceAggregate> findWithPagination(int offset, int limit);

    /**
     * Блокирует агрегат для конкурентного обновления
     * 
     * @param balanceId ID баланса
     * @return Optional с заблокированным агрегатом
     */
    Optional<BalanceAggregate> findByIdWithLock(BalanceId balanceId);

    /**
     * Пакетное сохранение агрегатов
     * 
     * @param balances список агрегатов для сохранения
     * @return список сохраненных агрегатов
     */
    List<BalanceAggregate> saveAll(List<BalanceAggregate> balances);

    /**
     * Очищает кэш репозитория (если используется)
     */
    void clearCache();
}