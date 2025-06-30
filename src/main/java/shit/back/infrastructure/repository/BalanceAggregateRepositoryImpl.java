package shit.back.infrastructure.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.repository.BalanceAggregateRepository;
import shit.back.domain.balance.BalanceAggregate;
import shit.back.domain.balance.BalancePolicy;
import shit.back.domain.balance.valueobjects.BalanceId;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.entity.UserBalanceEntity;
import shit.back.repository.UserBalanceJpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Реализация BalanceAggregateRepository в инфраструктурном слое
 * Преобразует доменные агрегаты в JPA сущности и обратно
 * Следует принципам DDD и Clean Architecture
 */
@Repository
@Transactional
public class BalanceAggregateRepositoryImpl implements BalanceAggregateRepository {

    private static final Logger log = LoggerFactory.getLogger(BalanceAggregateRepositoryImpl.class);

    private final UserBalanceJpaRepository userBalanceJpaRepository;
    private final BalancePolicy balancePolicy;

    public BalanceAggregateRepositoryImpl(
            UserBalanceJpaRepository userBalanceJpaRepository,
            BalancePolicy balancePolicy) {
        this.userBalanceJpaRepository = userBalanceJpaRepository;
        this.balancePolicy = balancePolicy;
    }

    @Override
    public BalanceAggregate save(BalanceAggregate balance) {
        log.debug("Сохранение агрегата баланса для пользователя {}", balance.getUserId());

        try {
            UserBalanceEntity entity = convertToEntity(balance);
            UserBalanceEntity savedEntity = userBalanceJpaRepository.save(entity);

            BalanceAggregate savedAggregate = convertToAggregate(savedEntity);
            log.debug("Агрегат баланса успешно сохранен с ID {}", savedEntity.getId());

            return savedAggregate;
        } catch (Exception e) {
            log.error("Ошибка при сохранении агрегата баланса для пользователя {}: {}",
                    balance.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Не удалось сохранить баланс", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BalanceAggregate> findById(BalanceId balanceId) {
        log.debug("Поиск агрегата баланса по ID {}", balanceId.getValue());

        return userBalanceJpaRepository.findById(balanceId.getValue())
                .map(this::convertToAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BalanceAggregate> findByUserId(Long userId) {
        log.debug("Поиск агрегата баланса по ID пользователя {}", userId);

        return userBalanceJpaRepository.findByUserId(userId)
                .map(this::convertToAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findByCurrency(String currency) {
        log.debug("Поиск агрегатов баланса по валюте {}", currency);

        return userBalanceJpaRepository.findByCurrency(currency)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findActiveByUserId(Long userId) {
        log.debug("Поиск активных балансов пользователя {}", userId);

        return userBalanceJpaRepository.findByUserId(userId)
                .stream()
                .filter(entity -> Boolean.TRUE.equals(entity.getIsActive()))
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(BalanceId balanceId) {
        return userBalanceJpaRepository.existsById(balanceId.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserId(Long userId) {
        return userBalanceJpaRepository.existsByUserId(userId);
    }

    @Override
    public void deleteById(BalanceId balanceId) {
        log.debug("Удаление агрегата баланса с ID {}", balanceId.getValue());
        userBalanceJpaRepository.deleteById(balanceId.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findWithBalanceGreaterThan(BigDecimal minAmount, String currency) {
        log.debug("Поиск балансов больше {} в валюте {}", minAmount, currency);

        return userBalanceJpaRepository
                .findByCurrentBalanceGreaterThanAndIsActiveTrueOrderByCurrentBalanceDesc(minAmount)
                .stream()
                .filter(entity -> currency.equals(entity.getCurrency()))
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findWithZeroBalance() {
        log.debug("Поиск балансов с нулевым значением");

        return userBalanceJpaRepository.findByCurrentBalanceAndIsActiveTrue(BigDecimal.ZERO)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findTopByBalance(int limit) {
        log.debug("Поиск топ {} балансов", limit);

        return userBalanceJpaRepository.findByIsActiveTrueOrderByLastUpdatedDesc()
                .stream()
                .limit(limit)
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveBalances() {
        return userBalanceJpaRepository.countByIsActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCurrency(String currency) {
        return userBalanceJpaRepository.findByCurrency(currency).size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findAll() {
        log.warn("Использование findAll() - может быть медленным для больших данных");

        return userBalanceJpaRepository.findAll()
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceAggregate> findWithPagination(int offset, int limit) {
        log.debug("Поиск балансов с пагинацией: offset={}, limit={}", offset, limit);

        return userBalanceJpaRepository.findAll()
                .stream()
                .skip(offset)
                .limit(limit)
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BalanceAggregate> findByIdWithLock(BalanceId balanceId) {
        log.debug("Поиск агрегата баланса с блокировкой по ID {}", balanceId.getValue());

        // Для простоты используем обычный поиск
        // В продакшене здесь должен быть SELECT ... FOR UPDATE
        return findById(balanceId);
    }

    @Override
    public List<BalanceAggregate> saveAll(List<BalanceAggregate> balances) {
        log.debug("Пакетное сохранение {} агрегатов баланса", balances.size());

        List<UserBalanceEntity> entities = balances.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());

        List<UserBalanceEntity> savedEntities = userBalanceJpaRepository.saveAll(entities);

        return savedEntities.stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    public void clearCache() {
        log.debug("Очистка кэша репозитория");
        // Реализация зависит от используемой системы кэширования
    }

    // Приватные методы для конвертации

    private UserBalanceEntity convertToEntity(BalanceAggregate aggregate) {
        UserBalanceEntity entity = new UserBalanceEntity();

        if (aggregate.getId() != null) {
            entity.setId(aggregate.getId());
        }

        entity.setUserId(aggregate.getUserId());
        entity.setCurrentBalance(aggregate.getCurrentBalance().getAmount());
        entity.setTotalDeposited(aggregate.getTotalDeposited().getAmount());
        entity.setTotalSpent(aggregate.getTotalSpent().getAmount());
        entity.setCurrency(aggregate.getCurrency().getCode());
        entity.setIsActive(aggregate.getIsActive());
        entity.setNotes(aggregate.getNotes());

        return entity;
    }

    private BalanceAggregate convertToAggregate(UserBalanceEntity entity) {
        BalanceAggregate aggregate = new BalanceAggregate(
                BalanceId.of(entity.getId()),
                entity.getUserId(),
                Currency.of(entity.getCurrency()),
                Money.of(entity.getCurrentBalance()),
                Money.of(entity.getTotalDeposited()),
                Money.of(entity.getTotalSpent()),
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getLastUpdated());

        aggregate.setNotes(entity.getNotes());
        aggregate.setBalancePolicy(balancePolicy);

        return aggregate;
    }
}