package shit.back.telegram.commands.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.dto.response.DualBalanceResponse;
import shit.back.application.balance.mapper.BalanceResponseMapper;
import shit.back.application.balance.service.BalanceApplicationServiceV2;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.dualBalance.DualBalanceAggregate;
import shit.back.domain.dualBalance.exceptions.BalanceTransferException;
import shit.back.domain.dualBalance.valueobjects.BalanceType;
import shit.back.service.UserSessionUnifiedService;
import shit.back.telegram.commands.TelegramCommandHandler;
import shit.back.telegram.commands.TransferFundsCommand;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;

/**
 * Обработчик команды перевода средств между балансами
 * 
 * Выполняет перевод с банковского баланса на основной баланс
 * для последующей покупки звезд через Fragment API
 */
@Component
@Slf4j
public class TransferFundsCommandHandler implements TelegramCommandHandler<TransferFundsCommand> {

    @Autowired
    private BalanceApplicationServiceV2 balanceApplicationService;

    @Autowired
    private UserSessionUnifiedService sessionService;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public TelegramResponse handle(TransferFundsCommand command) throws Exception {
        log.info("💸 Обработка перевода средств: userId={}, amount={}, sourceType={}",
                command.getUserId(), command.getAmount(), command.getSourceBalanceType());

        try {
            // Валидация команды
            command.validate();

            if (!command.hasAmount()) {
                // Команда "начать перевод" - переводим в состояние ввода суммы
                return handleTransferStart(command);
            } else {
                // Команда с суммой - обрабатываем перевод
                return handleTransferWithAmount(command);
            }

        } catch (IllegalArgumentException e) {
            log.warn("❌ Некорректные данные для перевода от пользователя {}: {}",
                    command.getUserId(), e.getMessage());
            return TelegramResponse.error("❌ " + e.getMessage());

        } catch (BalanceTransferException e) {
            log.warn("❌ Ошибка перевода средств для пользователя {}: {}",
                    command.getUserId(), e.getMessage());
            return TelegramResponse.error("❌ " + e.getMessage());

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке перевода средств для пользователя {}: {}",
                    command.getUserId(), e.getMessage(), e);
            return TelegramResponse.error("Не удалось обработать запрос на перевод средств: " + e.getMessage());
        }
    }

    @Override
    public Class<TransferFundsCommand> getCommandType() {
        return TransferFundsCommand.class;
    }

    @Override
    public int getHandlerPriority() {
        return 25; // Высокий приоритет для операций с балансом
    }

    @Override
    public String getDescription() {
        return "Обработчик команд перевода средств между банковским и основным балансами";
    }

    /**
     * Обработка команды "начать перевод"
     */
    private TelegramResponse handleTransferStart(TransferFundsCommand command) {
        try {
            // Получаем текущий баланс пользователя
            DualBalanceAggregate dualBalance = findOrCreateDualBalance(command.getUserId());

            if (!dualBalance.hasSufficientBankFunds(Money.of(new BigDecimal("0.01")))) {
                return TelegramResponse.error("❌ Недостаточно средств на банковском балансе для перевода");
            }

            // Обновляем состояние сессии
            sessionService.updateSessionState(command.getUserId(),
                    shit.back.model.UserSession.SessionState.ENTERING_CUSTOM_AMOUNT);

            String message = String.format("""
                    💸 <b>Перевод средств</b>

                    <b>Доступно для перевода:</b>
                    💳 Банковский баланс: <b>%s</b>
                    🏦 Основной баланс: <b>%s</b>

                    💡 Введите сумму для перевода с банковского на основной баланс (например: 100 или 50.25)

                    ⚠️ Минимальная сумма: 0.01
                    📊 Максимальная сумма: %s
                    """,
                    dualBalance.getBankBalance().getFormattedAmount(),
                    dualBalance.getMainBalance().getFormattedAmount(),
                    dualBalance.getBankBalance().getFormattedAmount());

            // Создаем клавиатуру с предустановленными суммами
            var keyboardBuilder = new TelegramKeyboardBuilder();

            // Добавляем кнопки с суммами в зависимости от доступного баланса
            Money bankBalance = dualBalance.getBankBalance();

            if (bankBalance.isGreaterThanOrEqual(Money.of(BigDecimal.TEN))) {
                keyboardBuilder.addButton("💸 10", "transfer_amount_10");
            }
            if (bankBalance.isGreaterThanOrEqual(Money.of(new BigDecimal("25")))) {
                keyboardBuilder.addButton("💸 25", "transfer_amount_25");
            }
            if (bankBalance.isGreaterThanOrEqual(Money.of(new BigDecimal("50")))) {
                keyboardBuilder.addButton("💸 50", "transfer_amount_50");
            }

            keyboardBuilder.newRow();

            if (bankBalance.isGreaterThanOrEqual(Money.of(new BigDecimal("100")))) {
                keyboardBuilder.addButton("💸 100", "transfer_amount_100");
            }
            if (bankBalance.isGreaterThanOrEqual(Money.of(new BigDecimal("250")))) {
                keyboardBuilder.addButton("💸 250", "transfer_amount_250");
            }
            if (bankBalance.isGreaterThanOrEqual(Money.of(new BigDecimal("500")))) {
                keyboardBuilder.addButton("💸 500", "transfer_amount_500");
            }

            keyboardBuilder.newRow()
                    .addButton("💸 Весь баланс", "transfer_amount_all")
                    .addButton("✏️ Свою сумму", "custom_transfer_amount")
                    .newRow()
                    .addButton("🔙 Назад", "show_balance");

            var keyboard = keyboardBuilder.build();

            return TelegramResponse.builder()
                    .successful(true)
                    .message(message)
                    .uiType("TRANSFER_AMOUNT_INPUT")
                    .uiData(command.getSourceBalanceType())
                    .data(keyboard)
                    .build();

        } catch (Exception e) {
            log.warn("⚠️ Не удалось инициировать процесс перевода для пользователя {}: {}",
                    command.getUserId(), e.getMessage());
            return TelegramResponse.error("Не удалось инициировать процесс перевода средств");
        }
    }

    /**
     * Обработка команды перевода с указанной суммой
     */
    private TelegramResponse handleTransferWithAmount(TransferFundsCommand command) throws Exception {
        // Получаем текущий баланс пользователя
        DualBalanceAggregate dualBalance = findOrCreateDualBalance(command.getUserId());
        Money transferAmount = Money.of(command.getAmount());

        // Проверяем достаточность средств
        if (!dualBalance.hasSufficientBankFunds(transferAmount)) {
            throw new BalanceTransferException("INSUFFICIENT_FUNDS",
                    String.format("Недостаточно средств на банковском балансе. Доступно: %s, требуется: %s",
                            dualBalance.getBankBalance().getFormattedAmount(),
                            transferAmount.getFormattedAmount()));
        }

        // Выполняем перевод
        String description = String.format("Перевод средств через Telegram Bot (пользователь %d)",
                command.getUserId());
        dualBalance.transferBankToMain(transferAmount, description);

        // Сохраняем изменения
        entityManager.merge(dualBalance);

        // Очищаем состояние сессии
        sessionService.updateSessionState(command.getUserId(),
                shit.back.model.UserSession.SessionState.IDLE);

        String message = String.format("""
                ✅ <b>Перевод выполнен успешно!</b>

                💸 <b>Переведено:</b> %s
                💳 <b>Банковский баланс:</b> %s
                🏦 <b>Основной баланс:</b> %s
                💰 <b>Общий баланс:</b> %s

                🎯 Теперь можете покупать звезды с основного баланса!
                """,
                transferAmount.getFormattedAmount(),
                dualBalance.getBankBalance().getFormattedAmount(),
                dualBalance.getMainBalance().getFormattedAmount(),
                dualBalance.getTotalBalance().getFormattedAmount());

        // Создаем клавиатуру с действиями после перевода
        var keyboard = new TelegramKeyboardBuilder()
                .addButton("⭐ Купить звезды", "buy_stars")
                .addButton("💸 Еще перевод", "transfer_funds")
                .newRow()
                .addButton("💰 Баланс", "show_balance")
                .addButton("📋 История", "show_history")
                .build();

        log.info("✅ Перевод средств выполнен успешно для пользователя {}: {} -> {}",
                command.getUserId(), transferAmount.getFormattedAmount(), description);

        return TelegramResponse.builder()
                .successful(true)
                .message(message)
                .uiType("TRANSFER_SUCCESS")
                .uiData(BalanceResponseMapper.fromDualBalance(dualBalance))
                .data(keyboard)
                .build();
    }

    /**
     * Поиск или создание DualBalanceAggregate для пользователя
     */
    private DualBalanceAggregate findOrCreateDualBalance(Long userId) {
        try {
            // Попытка найти существующий DualBalance
            DualBalanceAggregate existing = entityManager
                    .createQuery("SELECT db FROM DualBalanceAggregate db WHERE db.userId = :userId",
                            DualBalanceAggregate.class)
                    .setParameter("userId", userId)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                return existing;
            }

            // Создаем новый DualBalance если не найден
            log.info("Создание нового DualBalance для пользователя: {}", userId);
            DualBalanceAggregate newDualBalance = new DualBalanceAggregate(userId, null);
            entityManager.persist(newDualBalance);
            entityManager.flush(); // Получаем ID

            return newDualBalance;

        } catch (Exception e) {
            log.error("Ошибка при работе с DualBalance для пользователя {}: {}", userId, e.getMessage(), e);
            throw new BalanceTransferException("DUAL_BALANCE_ERROR",
                    "Не удалось получить информацию о балансе пользователя");
        }
    }
}