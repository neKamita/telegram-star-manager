package shit.back.infrastructure.balance.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.repository.BalanceAggregateRepository;
import shit.back.domain.balance.BalanceAggregate;
import shit.back.domain.balance.exceptions.InvalidTransactionException;
import shit.back.domain.balance.valueobjects.BalanceId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Реализация репозитория BalanceAggregate напрямую с JPA
 * 
 * ВАЖНО: BalanceAggregate уже является JPA Entity (@Entity),
 * поэтому мы можем использовать его напрямую без маппинга.
 * 
 * Обеспечивает:
 * - Работу с BalanceAggregate как JPA Entity
 * - Optimistic locking support
 * - Transaction management
 * - Error handling с domain exceptions
 */
@Repository
@Transactional
public class BalanceAggregateRepositoryImpl implements BalanceAggregateRepository {

    private static final Logger log = LoggerFactory.getLogger(BalanceAggregateRepositoryImpl.class);

    // ПРИМЕЧАНИЕ: Создадим отдельный JPA Repository для BalanceAggregate
    // Поскольку BalanceAggregate является @Entity, мы можем работать с ним напрямую

    @Autowired
    private BalanceAggregateJpaRepository balanceAggregateJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<BalanceAggregate> findById(BalanceId balanceId) {
        log.debug("Поиск баланса по ID: {}", balanceId.getValue());

        try {
            return balanceAggregateJpaRepository.findById(balanceId.getValue())
                    .map(aggregate -> {
                        log.debug("Найден баланс: userId={}, amount={}",
                                aggregate.getUserId(), aggregate.getCurrentBalance().getAmount());
                        return aggregate;
                    });
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске баланса ID {}: {}", balanceId.getValue(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BalanceAggregate> findByUserId(Long userId) {
        log.debug("Поиск баланса по userId: {}", userId);

        try {
            return balanceAggregateJpaRepository.findByUserId(userId)
                    .map(aggregate -> {
                        log.debug("Найден баланс пользователя {}: amount={}",
                                userId, aggregate.getCurrentBalance().getAmount());
                        return aggregate;
                    });
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске баланса пользователя {}: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    public BalanceAggregate save(BalanceAggregate balanceAggregate) {
        log.debug("Сохранение баланса: userId={}, amount={}",
                balanceAggregate.getUserId(), balanceAggregate.getCurrentBalance().getAmount());

        try {
            BalanceAggregate savedAggregate = balanceAggregateJpaRepository.save(balanceAggregate);

            log.debug("Баланс успешно сохранен: ID={}, userId={}",
                    savedAggregate.getId(), savedAggregate.getUserId());

            return savedAggregate;

        } catch (OptimisticLockingFailureException e) {
            log.error("Конфликт версий при сохранении баланса userId {}: {}",
                    balanceAggregate.getUserId(), e.getMessage());
            throw new InvalidTransactionException("OPTIMISTIC_LOCK_ERROR", e.getMessage(), "Корректная версия");
        } catch (DataAccessException e) {
            log.error("Ошибка при сохранении баланса userId {}: {}",
                    balanceAggregate.getUserId(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(BalanceId balanceId) {
        log.debug("Проверка существования баланса ID: {}", balanceId.getValue());

        try {
            boolean exists = balanceAggregateJpaRepository.existsById(balanceId.getValue());
            log.debug("Баланс ID {} {}", balanceId.getValue(), exists ? "существует" : "не найден");
            return exists;

        } catch (DataAccessException e) {
            log.error("Ошибка при проверке существования баланса ID {}: {}",
                    balanceId.getValue(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserId(Long userId) {
        log.debug("Проверка существования баланса пользователя: {}", userId);

        try {
            boolean exists = balanceAggregateJpaRepository.existsByUserId(userId);
            log.debug("Баланс пользователя {} {}", userId, exists ? "существует" : "не найден");
            return exists;

        } catch (DataAccessException e) {
            log.error("Ошибка при проверке существования баланса пользователя {}: {}",
                    userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findByCurrency(String currency) {
        log.debug("Поиск балансов по валюте: {}", currency);

        try {
            List<BalanceAggregate> aggregates = balanceAggregateJpaRepository.findByCurrency_Code(currency);
            log.debug("Найдено {} балансов с валютой {}", aggregates.size(), currency);
            return aggregates;

        } catch (DataAccessException e) {
            log.error("Ошибка при поиске балансов по валюте {}: {}", currency, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    public void deleteById(BalanceId balanceId) {
        log.debug("Удаление баланса ID: {}", balanceId.getValue());

        try {
            if (!balanceAggregateJpaRepository.existsById(balanceId.getValue())) {
                throw new InvalidTransactionException("BALANCE_NOT_FOUND",
                        balanceId.getValue().toString(), "Существующий ID баланса");
            }

            balanceAggregateJpaRepository.deleteById(balanceId.getValue());
            log.debug("Баланс ID {} успешно удален", balanceId.getValue());

        } catch (DataAccessException e) {
            log.error("Ошибка при удалении баланса ID {}: {}", balanceId.getValue(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findAll() {
        try {
            return balanceAggregateJpaRepository.findAll();
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске всех балансов: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findActiveByUserId(Long userId) {
        try {
            return balanceAggregateJpaRepository.findByUserIdAndIsActiveTrue(userId)
                    .map(List::of)
                    .orElse(List.of());
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске активного баланса пользователя {}: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    public List<BalanceAggregate> saveAll(List<BalanceAggregate> balances) {
        try {
            return balanceAggregateJpaRepository.saveAll(balances);
        } catch (DataAccessException e) {
            log.error("Ошибка при массовом сохранении балансов: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BalanceAggregate> findByIdWithLock(BalanceId balanceId) {
        try {
            return balanceAggregateJpaRepository.findByIdWithLock(balanceId.getValue());
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске баланса с блокировкой ID {}: {}", balanceId.getValue(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveBalances() {
        try {
            return balanceAggregateJpaRepository.countByIsActiveTrue();
        } catch (DataAccessException e) {
            log.error("Ошибка при подсчете активных балансов: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCurrency(String currency) {
        try {
            return balanceAggregateJpaRepository.countByCurrency_Code(currency);
        } catch (DataAccessException e) {
            log.error("Ошибка при подсчете балансов по валюте {}: {}", currency, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findWithBalanceGreaterThan(BigDecimal amount, String currency) {
        try {
            return balanceAggregateJpaRepository.findByCurrentBalance_AmountGreaterThanAndCurrency_Code(amount,
                    currency);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске балансов больше {}: {}", amount, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findWithZeroBalance() {
        try {
            return balanceAggregateJpaRepository.findByCurrentBalance_Amount(BigDecimal.ZERO);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске балансов с нулевым балансом: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findTopByBalance(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return balanceAggregateJpaRepository.findTopByOrderByCurrentBalance_AmountDesc(pageable);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске топ балансов: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findWithPagination(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            return balanceAggregateJpaRepository.findAllByOrderByLastUpdatedDesc(pageable);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске балансов с пагинацией: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    public void clearCache() {
        // В данной реализации кэш не используется
        log.debug("Очистка кэша (no-op в данной реализации)");
    }
}