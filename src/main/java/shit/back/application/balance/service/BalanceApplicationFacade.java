package shit.back.application.balance.service;

import shit.back.application.balance.common.Result;
import shit.back.application.balance.dto.request.*;
import shit.back.application.balance.dto.response.*;
import shit.back.domain.balance.valueobjects.Money;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Главный интерфейс Application Layer для Balance операций
 * 
 * Следует Interface Segregation Principle (ISP):
 * - Основной интерфейс содержит только core operations
 * - Специализированные интерфейсы наследуются от него
 * 
 * Заменяет legacy интерфейсы:
 * - BalanceOperations
 * - TransactionOperations
 * - BalanceManagementService
 */
public interface BalanceApplicationFacade extends
        BalanceCommandOperations,
        BalanceQueryOperations,
        BalanceAsyncOperations,
        BalanceBatchOperations {

    // Главный интерфейс объединяет все операции через наследование
    // Это позволяет клиентам выбирать только нужные им интерфейсы
}

/**
 * Command operations - Write operations (CQRS)
 */
interface BalanceCommandOperations {

    /**
     * Пополнение баланса
     */
    Result<BalanceResponse> processOperation(OperationRequest request);

    /**
     * Снятие с баланса
     */
    // остальные методы объединены в processOperation

    /**
     * Резервирование средств
     */
    // остальные методы объединены в processOperation

    /**
     * Освобождение резерва
     */
    // остальные методы объединены в processOperation

    /**
     * Возврат средств
     */
    // остальные методы объединены в processOperation

    /**
     * Административная корректировка
     */
    // остальные методы объединены в processOperation
}

/**
 * Query operations - Read operations (CQRS)
 */
interface BalanceQueryOperations {

    /**
     * Получение баланса пользователя
     */
    Result<BalanceResponse> getBalance(Long userId);

    /**
     * Получение истории транзакций
     */
    Result<List<TransactionResponse>> getTransactionHistory(Long userId, int page, int size);

    /**
     * Получение статистики баланса
     */
    Result<BalanceStatisticsResponse> getBalanceStatistics(Long userId);

    /**
     * Проверка достаточности средств
     */
    Result<Boolean> checkSufficientFunds(Long userId, Money amount);
}

/**
 * Async operations - Асинхронные операции для performance
 */
interface BalanceAsyncOperations {

    /**
     * Асинхронное пополнение баланса
     */
    CompletableFuture<Result<BalanceResponse>> processOperationAsync(OperationRequest request);

    /**
     * Асинхронное снятие с баланса
     */
    // остальные методы объединены в processOperationAsync
}

/**
 * Batch operations - Массовые операции для performance
 */
interface BalanceBatchOperations {

    /**
     * Batch обработка операций
     */
    Result<List<BalanceResponse>> processBatchOperations(List<OperationRequest> requests);
}