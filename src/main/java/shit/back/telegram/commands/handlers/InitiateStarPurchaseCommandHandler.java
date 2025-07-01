package shit.back.telegram.commands.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.service.BalanceApplicationFacade;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.application.balance.mapper.BalanceResponseMapper;
import shit.back.config.StarPriceConstants;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.service.StarPurchaseService;
import shit.back.service.UserSessionUnifiedService;
import shit.back.telegram.commands.InitiateStarPurchaseCommand;
import shit.back.telegram.commands.TelegramCommandHandler;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.ui.strategy.SimplifiedStarPurchaseStrategy;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

/**
 * Обработчик команды инициации покупки звезд
 * 
 * Использует упрощенную архитектуру без legacy кода.
 * Применяет принципы SOLID, DRY, Clean Code, KISS.
 */
@Component
@Slf4j
public class InitiateStarPurchaseCommandHandler implements TelegramCommandHandler<InitiateStarPurchaseCommand> {

    @Autowired
    private BalanceApplicationFacade balanceApplicationFacade;

    @Autowired
    private SimplifiedStarPurchaseStrategy simplifiedStarPurchaseStrategy;

    @Autowired
    private StarPurchaseService starPurchaseService;

    @Autowired
    private UserSessionUnifiedService sessionService;

    @Override
    @Transactional
    public TelegramResponse handle(InitiateStarPurchaseCommand command) throws Exception {
        log.info("⭐ Инициация покупки звезд: userId={}, stars={}, amount={}, confirm={}",
                command.getUserId(), command.getStarCount(), command.getCustomAmount(), command.isConfirmPurchase());

        try {
            command.validate();

            SimpleBalanceResponse balance = getSimpleBalance(command.getUserId());
            if (balance == null) {
                return TelegramResponse.error("Не удалось получить информацию о балансе");
            }

            // ДИАГНОСТИКА: Логируем тип операции для отладки
            String operationType = command.getOperationType();
            log.info(
                    "🔍 ДИАГНОСТИКА: Получен тип операции '{}' для команды: userId={}, starCount={}, customAmount={}, confirmPurchase={}",
                    operationType, command.getUserId(), command.getStarCount(), command.getCustomAmount(),
                    command.isConfirmPurchase());

            return switch (operationType) {
                case "PURCHASE_CONFIRMATION" -> handlePurchaseConfirmation(command, balance);
                case "PURCHASE_INTERFACE" -> handlePurchaseInterface(balance);
                default -> {
                    log.error("❌ ДИАГНОСТИКА: Неподдерживаемый тип операции '{}' для команды: {}", operationType,
                            command);
                    yield TelegramResponse.error("Неподдерживаемый тип операции: " + operationType);
                }
            };

        } catch (IllegalArgumentException e) {
            log.warn("❌ Некорректная команда покупки звезд от пользователя {}: {}",
                    command.getUserId(), e.getMessage());
            return TelegramResponse.error("❌ " + e.getMessage());

        } catch (Exception e) {
            log.error("❌ Ошибка при инициации покупки звезд для пользователя {}: {}",
                    command.getUserId(), e.getMessage(), e);
            return TelegramResponse.error("Не удалось инициировать покупку звезд: " + e.getMessage());
        }
    }

    @Override
    public Class<InitiateStarPurchaseCommand> getCommandType() {
        return InitiateStarPurchaseCommand.class;
    }

    @Override
    public int getHandlerPriority() {
        return 5;
    }

    @Override
    public String getDescription() {
        return "Обработчик команд покупки звезд с упрощенной архитектурой";
    }

    /**
     * Обработка подтверждения покупки
     */
    private TelegramResponse handlePurchaseConfirmation(InitiateStarPurchaseCommand command,
            SimpleBalanceResponse balance) throws Exception {
        log.info("✅ Подтверждение покупки звезд: userId={}, stars={}",
                command.getUserId(), command.getEffectiveStarCount());

        Money requiredAmount = getRequiredAmount(command);
        Integer starCount = command.getEffectiveStarCount();

        // Проверяем достаточность средств
        if (!balance.hasSufficientFunds(requiredAmount)) {
            return createInsufficientFundsResponse(command, balance, requiredAmount, starCount);
        }

        // Выполняем покупку через StarPurchaseService
        var purchaseResult = starPurchaseService.purchaseStars(command.getUserId(), starCount, requiredAmount);

        if (purchaseResult.isSuccess()) {
            log.info("✅ Покупка звезд успешна: userId={}, stars={}, transactionId={}",
                    command.getUserId(), starCount, purchaseResult.getTransactionId());

            return createSuccessResponse(starCount, requiredAmount, balance, purchaseResult);
        } else {
            log.error("❌ Ошибка покупки: {}", purchaseResult.getErrorMessage());
            return TelegramResponse.error("Не удалось выполнить покупку: " + purchaseResult.getErrorMessage());
        }
    }

    /**
     * Обработка интерфейса покупки
     */
    private TelegramResponse handlePurchaseInterface(SimpleBalanceResponse balance) {
        try {
            updateSessionState(balance.getUserId(), "STAR_PURCHASE_INTERFACE");

            var uiResponse = simplifiedStarPurchaseStrategy.createStarPurchaseFlow(balance);

            return TelegramResponse.builder()
                    .successful(true)
                    .message(uiResponse.getMessageText())
                    .uiType("PURCHASE_INTERFACE")
                    .uiData(balance)
                    .data(uiResponse.getKeyboard())
                    .build();

        } catch (Exception e) {
            log.error("❌ Ошибка создания интерфейса покупки: {}", e.getMessage(), e);
            return TelegramResponse.error("Ошибка при создании интерфейса покупки: " + e.getMessage());
        }
    }

    /**
     * Получение требуемой суммы для покупки
     */
    private Money getRequiredAmount(InitiateStarPurchaseCommand command) {
        if (command.getCustomAmount() != null) {
            return command.getCustomAmount();
        }

        Integer starCount = command.getEffectiveStarCount();
        if (StarPriceConstants.isSupportedStarCount(starCount)) {
            return StarPriceConstants.getPriceForStars(starCount);
        }

        throw new IllegalArgumentException("Неподдерживаемое количество звезд: " + starCount);
    }

    /**
     * Получение простого баланса пользователя
     */
    private SimpleBalanceResponse getSimpleBalance(Long userId) {
        try {
            var balanceResult = balanceApplicationFacade.getBalance(userId);

            if (balanceResult != null && balanceResult.isSuccess()) {
                log.debug("💰 Получен результат баланса для пользователя: {}", userId);

                if (balanceResult.getValue() instanceof shit.back.application.balance.dto.response.BalanceResponse) {
                    var balanceResponse = (shit.back.application.balance.dto.response.BalanceResponse) balanceResult
                            .getValue();
                    return BalanceResponseMapper.toSimpleBalanceResponse(balanceResponse);
                }
            }

            log.warn("⚠️ Не удалось получить баланс пользователя: {}", userId);
            return BalanceResponseMapper.createEmptyBalance(userId);

        } catch (Exception e) {
            log.error("❌ Ошибка получения баланса пользователя {}: {}", userId, e.getMessage(), e);
            return BalanceResponseMapper.createEmptyBalance(userId);
        }
    }

    /**
     * Создание ответа о недостаточности средств
     */
    private TelegramResponse createInsufficientFundsResponse(InitiateStarPurchaseCommand command,
            SimpleBalanceResponse balance, Money required, Integer starCount) {
        log.warn("💸 Недостаточно средств для покупки звезд: userId={}, required={}, available={}",
                command.getUserId(), required, balance.getCurrentBalance());

        String currencySymbol = balance.getCurrency().getSymbol();
        Money shortfall = required.subtract(balance.getCurrentBalance());

        String message = String.format("""
                ❌ <b>Недостаточно средств</b>

                ⭐ <b>Запрошено:</b> %d звезд
                💰 <b>Требуется:</b> %s %s
                💼 <b>Доступно:</b> %s
                💸 <b>Не хватает:</b> %s %s

                💡 Пополните баланс и возвращайтесь!
                """, starCount, required.getFormattedAmount(), currencySymbol,
                balance.getFormattedBalance(), shortfall.getFormattedAmount(), currencySymbol);

        var keyboard = new TelegramKeyboardBuilder()
                .addButton("💳 Пополнить " + shortfall.getFormattedAmount(),
                        "topup_amount_" + shortfall.getFormattedAmount())
                .newRow()
                .addButton("💳 Пополнить баланс", "topup_balance")
                .addButton("🔙 Назад", "show_balance")
                .build();

        return TelegramResponse.builder()
                .successful(true)
                .message(message)
                .uiType("INSUFFICIENT_FUNDS")
                .data(keyboard)
                .build();
    }

    /**
     * Создание ответа об успешной покупке
     */
    private TelegramResponse createSuccessResponse(Integer starCount, Money amount,
            SimpleBalanceResponse balance, Object purchaseResult) {
        String currencySymbol = balance.getCurrency().getSymbol();

        String successMessage = String.format("""
                🌟 <b>Покупка звезд завершена!</b>

                ⭐ Куплено звезд: <b>%d</b>
                💰 Списано с баланса: <b>%s %s</b>

                ✅ Звезды добавлены в ваш аккаунт Telegram!
                """, starCount, amount.getFormattedAmount(), currencySymbol);

        var keyboard = new TelegramKeyboardBuilder()
                .addButton("💰 Показать баланс", "show_balance")
                .addButton("📋 История покупок", "purchase_history")
                .newRow()
                .addButton("🌟 Купить еще", "buy_stars")
                .build();

        return TelegramResponse.builder()
                .successful(true)
                .message(successMessage)
                .uiType("PURCHASE_SUCCESS")
                .uiData(purchaseResult)
                .data(keyboard)
                .build();
    }

    /**
     * Обновление состояния сессии пользователя
     */
    private void updateSessionState(Long userId, String state) {
        try {
            log.debug("🔄 Обновление состояния сессии: userId={}, state={}", userId, state);
            sessionService.getOrCreateSession(userId, "TelegramBot", "star_purchase", state);
        } catch (Exception e) {
            log.warn("⚠️ Не удалось обновить состояние сессии для пользователя {}: {}", userId, e.getMessage());
        }
    }
}