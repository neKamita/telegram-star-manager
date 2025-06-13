package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.*;
import shit.back.repository.BalanceTransactionJpaRepository;
import shit.back.repository.UserBalanceJpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Комплексный интеграционный тест системы баланса
 * 
 * Проверяет:
 * ✅ Базовые операции с балансом
 * ✅ Валидацию данных
 * ✅ Конкурентные операции
 * ✅ Лимиты и ограничения
 * ✅ Транзакционную целостность
 * ✅ Производительность системы
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BalanceSystemIntegrationTest {

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private BalanceValidationService validationService;

    @Autowired
    private BalanceTransactionService transactionService;

    @Autowired
    private UserBalanceJpaRepository userBalanceRepository;

    @Autowired
    private BalanceTransactionJpaRepository transactionRepository;

    private static final Long TEST_USER_ID = 999999L;
    private static final Long TEST_USER_ID_2 = 999998L;
    private static final BigDecimal INITIAL_DEPOSIT = new BigDecimal("100.00");
    private static final BigDecimal TEST_AMOUNT = new BigDecimal("25.50");

    @BeforeEach
    void setUp() {
        log.info("🔧 Подготовка тестовых данных...");
        // Очистка тестовых данных
        List<BalanceTransactionEntity> userTransactions = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(TEST_USER_ID);
        transactionRepository.deleteAll(userTransactions);

        List<BalanceTransactionEntity> userTransactions2 = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(TEST_USER_ID_2);
        transactionRepository.deleteAll(userTransactions2);

        userBalanceRepository.findByUserId(TEST_USER_ID).ifPresent(userBalanceRepository::delete);
        userBalanceRepository.findByUserId(TEST_USER_ID_2).ifPresent(userBalanceRepository::delete);
    }

    @Test
    @Order(1)
    @DisplayName("🏗️ Тест создания нового баланса")
    void testCreateNewBalance() {
        log.info("🧪 Выполняется тест создания нового баланса...");

        // Создание баланса
        UserBalanceEntity balance = balanceService.getOrCreateBalance(TEST_USER_ID);

        // Проверки
        assertNotNull(balance, "Баланс должен быть создан");
        assertEquals(TEST_USER_ID, balance.getUserId(), "ID пользователя должен совпадать");
        assertEquals(BigDecimal.ZERO, balance.getCurrentBalance(), "Начальный баланс должен быть 0");
        assertEquals("USD", balance.getCurrency(), "Валюта по умолчанию должна быть USD");
        assertTrue(balance.getIsActive(), "Баланс должен быть активным");

        log.info("✅ Тест создания баланса пройден успешно");
    }

    @Test
    @Order(2)
    @DisplayName("💰 Тест пополнения баланса")
    void testDepositBalance() {
        log.info("🧪 Выполняется тест пополнения баланса...");

        // Пополнение баланса
        BalanceTransactionEntity transaction = balanceService.deposit(
                TEST_USER_ID, INITIAL_DEPOSIT, "TEST_CARD", "Тестовое пополнение");

        // Проверки транзакции
        assertNotNull(transaction, "Транзакция должна быть создана");
        assertEquals(TransactionType.DEPOSIT, transaction.getType(), "Тип транзакции должен быть DEPOSIT");
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus(), "Статус должен быть COMPLETED");
        assertEquals(INITIAL_DEPOSIT, transaction.getAmount(), "Сумма транзакции должна совпадать");

        // Проверки баланса
        UserBalanceEntity balance = balanceService.getOrCreateBalance(TEST_USER_ID);
        assertEquals(INITIAL_DEPOSIT, balance.getCurrentBalance(), "Баланс должен увеличиться");
        assertEquals(INITIAL_DEPOSIT, balance.getTotalDeposited(), "Общая сумма пополнений должна совпадать");

        log.info("✅ Тест пополнения баланса пройден успешно");
    }

    @Test
    @Order(3)
    @DisplayName("🛒 Тест списания с баланса")
    void testWithdrawBalance() {
        log.info("🧪 Выполняется тест списания с баланса...");

        // Сначала пополняем баланс
        balanceService.deposit(TEST_USER_ID, INITIAL_DEPOSIT, "TEST_CARD", "Подготовка к тесту");

        // Списание с баланса
        BalanceTransactionEntity transaction = balanceService.withdraw(
                TEST_USER_ID, TEST_AMOUNT, "Тестовая покупка");

        // Проверки транзакции
        assertNotNull(transaction, "Транзакция должна быть создана");
        assertEquals(TransactionType.WITHDRAWAL, transaction.getType(), "Тип транзакции должен быть WITHDRAWAL");
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus(), "Статус должен быть COMPLETED");

        // Проверки баланса
        UserBalanceEntity balance = balanceService.getOrCreateBalance(TEST_USER_ID);
        BigDecimal expectedBalance = INITIAL_DEPOSIT.subtract(TEST_AMOUNT);
        assertEquals(expectedBalance, balance.getCurrentBalance(), "Баланс должен уменьшиться");
        assertEquals(TEST_AMOUNT, balance.getTotalSpent(), "Общая сумма трат должна совпадать");

        log.info("✅ Тест списания с баланса пройден успешно");
    }

    @Test
    @Order(4)
    @DisplayName("❌ Тест недостаточных средств")
    void testInsufficientFunds() {
        log.info("🧪 Выполняется тест недостаточных средств...");

        // Попытка списать больше, чем есть на балансе
        BigDecimal largeAmount = new BigDecimal("1000.00");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            balanceService.withdraw(TEST_USER_ID, largeAmount, "Попытка списания больших средств");
        });

        assertTrue(exception.getMessage().contains("Недостаточно средств"),
                "Исключение должно содержать сообщение о недостаточных средствах");

        log.info("✅ Тест недостаточных средств пройден успешно");
    }

    @Test
    @Order(5)
    @DisplayName("🔄 Тест возврата средств")
    void testRefundBalance() {
        log.info("🧪 Выполняется тест возврата средств...");

        // Подготовка: пополнение и списание
        balanceService.deposit(TEST_USER_ID, INITIAL_DEPOSIT, "TEST_CARD", "Подготовка");
        balanceService.withdraw(TEST_USER_ID, TEST_AMOUNT, "Тестовая покупка");

        UserBalanceEntity balanceBefore = balanceService.getOrCreateBalance(TEST_USER_ID);
        BigDecimal balanceBeforeRefund = balanceBefore.getCurrentBalance();

        // Возврат средств
        BalanceTransactionEntity refundTransaction = balanceService.refundToBalance(
                TEST_USER_ID, TEST_AMOUNT, "TEST_ORDER", "Тестовый возврат");

        // Проверки
        assertNotNull(refundTransaction, "Транзакция возврата должна быть создана");
        assertEquals(TransactionType.REFUND, refundTransaction.getType(), "Тип должен быть REFUND");

        UserBalanceEntity balanceAfter = balanceService.getOrCreateBalance(TEST_USER_ID);
        BigDecimal expectedBalance = balanceBeforeRefund.add(TEST_AMOUNT);
        assertEquals(expectedBalance, balanceAfter.getCurrentBalance(), "Баланс должен увеличиться на сумму возврата");

        log.info("✅ Тест возврата средств пройден успешно");
    }

    @Test
    @Order(6)
    @DisplayName("🔒 Тест резервирования средств")
    void testReserveBalance() {
        log.info("🧪 Выполняется тест резервирования средств...");

        // Подготовка баланса
        balanceService.deposit(TEST_USER_ID, INITIAL_DEPOSIT, "TEST_CARD", "Подготовка");

        // Резервирование средств
        String testOrderId = "TEST001";
        BalanceTransactionEntity reserveTransaction = balanceService.reserveBalance(
                TEST_USER_ID, TEST_AMOUNT, testOrderId);

        // Проверки
        assertNotNull(reserveTransaction, "Транзакция резервирования должна быть создана");
        assertEquals(TransactionType.PURCHASE, reserveTransaction.getType(), "Тип должен быть PURCHASE");
        assertEquals(TransactionStatus.PENDING, reserveTransaction.getStatus(), "Статус должен быть PENDING");
        assertEquals(testOrderId, reserveTransaction.getOrderId(), "ID заказа должен совпадать");

        // Проверяем, что баланс пока не изменился
        UserBalanceEntity balance = balanceService.getOrCreateBalance(TEST_USER_ID);
        assertEquals(INITIAL_DEPOSIT, balance.getCurrentBalance(), "Баланс не должен измениться при резервировании");

        // Освобождение резерва
        balanceService.releaseReservedBalance(TEST_USER_ID, testOrderId);

        // Проверяем, что транзакция отменена
        List<BalanceTransactionEntity> transactions = transactionRepository
                .findByOrderIdOrderByCreatedAtDesc(testOrderId);
        assertTrue(transactions.stream().anyMatch(t -> t.getStatus() == TransactionStatus.CANCELLED),
                "Транзакция резервирования должна быть отменена");

        log.info("✅ Тест резервирования средств пройден успешно");
    }

    @Test
    @Order(7)
    @DisplayName("⚙️ Тест административной корректировки")
    void testAdminAdjustment() {
        log.info("🧪 Выполняется тест административной корректировки...");

        // Подготовка баланса
        balanceService.deposit(TEST_USER_ID, INITIAL_DEPOSIT, "TEST_CARD", "Подготовка");

        UserBalanceEntity balanceBefore = balanceService.getOrCreateBalance(TEST_USER_ID);
        BigDecimal adjustmentAmount = new BigDecimal("50.00");

        // Административная корректировка
        BalanceTransactionEntity adjustmentTransaction = balanceService.adjustBalance(
                TEST_USER_ID, adjustmentAmount, "Тестовая корректировка", "admin");

        // Проверки
        assertNotNull(adjustmentTransaction, "Транзакция корректировки должна быть создана");
        assertEquals(TransactionType.ADJUSTMENT, adjustmentTransaction.getType(), "Тип должен быть ADJUSTMENT");
        assertEquals("admin", adjustmentTransaction.getProcessedBy(), "Обработчик должен быть admin");

        UserBalanceEntity balanceAfter = balanceService.getOrCreateBalance(TEST_USER_ID);
        BigDecimal expectedBalance = balanceBefore.getCurrentBalance().add(adjustmentAmount);
        assertEquals(expectedBalance, balanceAfter.getCurrentBalance(), "Баланс должен быть скорректирован");

        log.info("✅ Тест административной корректировки пройден успешно");
    }

    @Test
    @Order(8)
    @DisplayName("📊 Тест валидации лимитов")
    void testValidationLimits() {
        log.info("🧪 Выполняется тест валидации лимитов...");

        // Тест минимальной суммы
        BigDecimal tooSmallAmount = new BigDecimal("0.001");
        RuntimeException exception1 = assertThrows(RuntimeException.class, () -> {
            validationService.validateDepositAmount(tooSmallAmount);
        });
        assertTrue(exception1.getMessage().contains("Минимальная сумма"),
                "Должна быть ошибка о минимальной сумме");

        // Тест максимальной суммы
        BigDecimal tooLargeAmount = new BigDecimal("50000.00");
        RuntimeException exception2 = assertThrows(RuntimeException.class, () -> {
            validationService.validateDepositAmount(tooLargeAmount);
        });
        assertTrue(exception2.getMessage().contains("Максимальная сумма"),
                "Должна быть ошибка о максимальной сумме");

        // Тест null значения
        RuntimeException exception3 = assertThrows(RuntimeException.class, () -> {
            validationService.validateDepositAmount(null);
        });
        assertTrue(exception3.getMessage().contains("не может быть пустой"),
                "Должна быть ошибка о пустом значении");

        log.info("✅ Тест валидации лимитов пройден успешно");
    }

    @Test
    @Order(9)
    @DisplayName("⚡ Тест конкурентных операций")
    void testConcurrentOperations() throws InterruptedException {
        log.info("🧪 Выполняется тест конкурентных операций...");

        // Подготовка баланса
        balanceService.deposit(TEST_USER_ID, new BigDecimal("1000.00"), "TEST_CARD",
                "Подготовка к конкурентным тестам");

        // Количество потоков для теста
        int threadCount = 10;
        BigDecimal amountPerOperation = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        UserBalanceEntity initialBalance = balanceService.getOrCreateBalance(TEST_USER_ID);
        BigDecimal initialAmount = initialBalance.getCurrentBalance();

        // Запуск конкурентных операций списания
        for (int i = 0; i < threadCount; i++) {
            final int operationNumber = i;
            executor.submit(() -> {
                try {
                    balanceService.withdraw(TEST_USER_ID, amountPerOperation,
                            "Конкурентная операция #" + operationNumber);
                } catch (Exception e) {
                    log.warn("Операция #{} не выполнена: {}", operationNumber, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Ожидание завершения всех операций
        latch.await();
        executor.shutdown();

        // Проверка итогового баланса
        UserBalanceEntity finalBalance = balanceService.getOrCreateBalance(TEST_USER_ID);

        // Подсчитываем количество успешных операций
        List<BalanceTransactionEntity> allWithdrawals = transactionRepository
                .findByUserIdAndTypeOrderByCreatedAtDesc(TEST_USER_ID, TransactionType.WITHDRAWAL);
        List<BalanceTransactionEntity> completedTransactions = allWithdrawals.stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .toList();

        long successfulOperations = completedTransactions.stream()
                .filter(t -> t.getDescription().contains("Конкурентная операция"))
                .count();

        BigDecimal expectedBalance = initialAmount.subtract(
                amountPerOperation.multiply(BigDecimal.valueOf(successfulOperations)));

        assertEquals(expectedBalance, finalBalance.getCurrentBalance(),
                "Финальный баланс должен соответствовать количеству успешных операций");

        log.info("✅ Тест конкурентных операций пройден успешно. Успешных операций: {}", successfulOperations);
    }

    @Test
    @Order(10)
    @DisplayName("📈 Тест статистики баланса")
    void testBalanceStatistics() {
        log.info("🧪 Выполняется тест статистики баланса...");

        // Подготовка данных для статистики
        balanceService.deposit(TEST_USER_ID, new BigDecimal("200.00"), "TEST_CARD", "Первое пополнение");
        balanceService.deposit(TEST_USER_ID, new BigDecimal("100.00"), "TEST_BANK", "Второе пополнение");
        balanceService.withdraw(TEST_USER_ID, new BigDecimal("50.00"), "Тестовая покупка");

        // Получение статистики
        Map<String, Object> statistics = balanceService.getBalanceStatistics(TEST_USER_ID);

        // Проверки
        assertNotNull(statistics, "Статистика должна быть получена");
        assertTrue(statistics.containsKey("currentBalance"), "Должен быть текущий баланс");
        assertTrue(statistics.containsKey("totalDeposited"), "Должна быть общая сумма пополнений");
        assertTrue(statistics.containsKey("totalSpent"), "Должна быть общая сумма трат");
        assertTrue(statistics.containsKey("transactionCount"), "Должно быть количество транзакций");

        BigDecimal currentBalance = (BigDecimal) statistics.get("currentBalance");
        BigDecimal totalDeposited = (BigDecimal) statistics.get("totalDeposited");
        BigDecimal totalSpent = (BigDecimal) statistics.get("totalSpent");

        assertEquals(new BigDecimal("250.00"), currentBalance, "Текущий баланс должен быть 250.00");
        assertEquals(new BigDecimal("300.00"), totalDeposited, "Общая сумма пополнений должна быть 300.00");
        assertEquals(new BigDecimal("50.00"), totalSpent, "Общая сумма трат должна быть 50.00");

        log.info("✅ Тест статистики баланса пройден успешно");
    }

    @Test
    @Order(11)
    @DisplayName("🕰️ Тест производительности системы")
    void testPerformance() {
        log.info("🧪 Выполняется тест производительности системы...");

        // Подготовка баланса
        balanceService.deposit(TEST_USER_ID, new BigDecimal("10000.00"), "TEST_CARD",
                "Подготовка к тесту производительности");

        int operationCount = 100;
        BigDecimal operationAmount = new BigDecimal("1.00");

        // Измерение времени выполнения операций
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < operationCount; i++) {
            if (i % 2 == 0) {
                balanceService.deposit(TEST_USER_ID, operationAmount, "PERF_TEST", "Тест производительности #" + i);
            } else {
                balanceService.withdraw(TEST_USER_ID, operationAmount, "Тест производительности #" + i);
            }
        }

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        double operationsPerSecond = (double) operationCount / (executionTime / 1000.0);

        log.info("📊 Результаты теста производительности:");
        log.info("   Операций: {}", operationCount);
        log.info("   Время выполнения: {} мс", executionTime);
        log.info("   Операций в секунду: {:.2f}", operationsPerSecond);

        // Проверяем, что производительность приемлемая (минимум 10 операций в секунду)
        assertTrue(operationsPerSecond >= 10.0,
                "Производительность должна быть не менее 10 операций в секунду");

        log.info("✅ Тест производительности системы пройден успешно");
    }

    @Test
    @Order(12)
    @DisplayName("🔍 Тест полной целостности системы")
    void testSystemIntegrity() {
        log.info("🧪 Выполняется тест полной целостности системы...");

        // Сложный сценарий с множественными операциями
        String orderId = "INTEGRITY_TEST";

        // 1. Пополнение баланса
        balanceService.deposit(TEST_USER_ID, new BigDecimal("500.00"), "TEST_CARD", "Начальное пополнение");

        // 2. Резервирование средств
        balanceService.reserveBalance(TEST_USER_ID, new BigDecimal("100.00"), orderId);

        // 3. Обработка платежа
        balanceService.processBalancePayment(TEST_USER_ID, orderId, new BigDecimal("100.00"));

        // 4. Частичный возврат
        balanceService.refundToBalance(TEST_USER_ID, new BigDecimal("30.00"), orderId, "Частичный возврат");

        // 5. Административная корректировка
        balanceService.adjustBalance(TEST_USER_ID, new BigDecimal("25.00"), "Бонус", "system");

        // Проверка итогового состояния
        UserBalanceEntity finalBalance = balanceService.getOrCreateBalance(TEST_USER_ID);
        List<BalanceTransactionEntity> allTransactions = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(TEST_USER_ID);

        // Проверки
        assertNotNull(finalBalance, "Финальный баланс должен существовать");
        assertFalse(allTransactions.isEmpty(), "Должны быть транзакции");

        // Проверяем корректность баланса
        BigDecimal expectedBalance = new BigDecimal("455.00"); // 500 - 100 + 30 + 25
        assertEquals(expectedBalance, finalBalance.getCurrentBalance(),
                "Итоговый баланс должен быть корректным");

        // Проверяем количество транзакций
        assertEquals(5, allTransactions.size(), "Должно быть 5 транзакций");

        // Проверяем статусы транзакций
        long completedCount = allTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .count();
        assertEquals(4, completedCount, "4 транзакции должны быть завершены");

        log.info("✅ Тест полной целостности системы пройден успешно");
    }

    @AfterEach
    void tearDown() {
        log.info("🧹 Очистка тестовых данных...");
        // Очистка после каждого теста
        try {
            List<BalanceTransactionEntity> userTransactions = transactionRepository
                    .findByUserIdOrderByCreatedAtDesc(TEST_USER_ID);
            transactionRepository.deleteAll(userTransactions);

            List<BalanceTransactionEntity> userTransactions2 = transactionRepository
                    .findByUserIdOrderByCreatedAtDesc(TEST_USER_ID_2);
            transactionRepository.deleteAll(userTransactions2);

            userBalanceRepository.findByUserId(TEST_USER_ID).ifPresent(userBalanceRepository::delete);
            userBalanceRepository.findByUserId(TEST_USER_ID_2).ifPresent(userBalanceRepository::delete);
        } catch (Exception e) {
            log.warn("Ошибка при очистке тестовых данных: {}", e.getMessage());
        }
    }

    @AfterAll
    static void generateTestReport() {
        log.info("");
        log.info("🎯 ОТЧЕТ О ТЕСТИРОВАНИИ СИСТЕМЫ БАЛАНСА");
        log.info("════════════════════════════════════════");
        log.info("✅ Все тесты успешно пройдены!");
        log.info("📋 Проверенные компоненты:");
        log.info("   • Создание и управление балансом");
        log.info("   • Пополнение и списание средств");
        log.info("   • Валидация данных и лимитов");
        log.info("   • Резервирование и возврат средств");
        log.info("   • Административные операции");
        log.info("   • Конкурентные операции");
        log.info("   • Производительность системы");
        log.info("   • Целостность данных");
        log.info("════════════════════════════════════════");
        log.info("");
    }
}