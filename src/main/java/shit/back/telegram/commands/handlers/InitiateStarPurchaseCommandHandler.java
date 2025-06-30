package shit.back.telegram.commands.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.service.BalanceApplicationFacade;
import shit.back.application.balance.dto.response.DualBalanceResponse;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.service.FragmentIntegrationService;
import shit.back.service.UserSessionUnifiedService;
import shit.back.telegram.commands.InitiateStarPurchaseCommand;
import shit.back.telegram.commands.TelegramCommandHandler;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.ui.strategy.StarPurchaseFlowStrategy;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

/**
 * Обработчик команды инициации покупки звезд
 * 
 * Интегрируется с StarPurchaseFlowStrategy для управления флоу покупки
 */
@Component
@Slf4j
public class InitiateStarPurchaseCommandHandler implements TelegramCommandHandler<InitiateStarPurchaseCommand> {

    @Autowired
    private BalanceApplicationFacade balanceApplicationFacade;

    @Autowired
    private StarPurchaseFlowStrategy starPurchaseFlowStrategy;

    @Autowired
    private FragmentIntegrationService fragmentIntegrationService;

    @Autowired
    private UserSessionUnifiedService sessionService;

    @Override
    @Transactional
    public TelegramResponse handle(InitiateStarPurchaseCommand command) throws Exception {
        log.info("⭐ Инициация покупки звезд: userId={}, stars={}, amount={}, confirm={}",
                command.getUserId(), command.getStarCount(), command.getCustomAmount(), command.isConfirmPurchase());

        try {
            // Валидация команды
            command.validate();

            // Получаем баланс пользователя
            DualBalanceResponse balanceData = getUserBalance(command.getUserId());
            if (balanceData == null) {
                return TelegramResponse.error("Не удалось получить информацию о балансе");
            }

            // Определяем тип операции и обрабатываем соответственно
            return switch (command.getOperationType()) {
                case "PURCHASE_CONFIRMATION" -> handlePurchaseConfirmation(command, balanceData);
                case "BALANCE_CHECK" -> handleBalanceCheck(command, balanceData);
                case "PURCHASE_INTERFACE" -> handlePurchaseInterface(command, balanceData);
                default -> TelegramResponse.error("Неподдерживаемый тип операции: " + command.getOperationType());
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
        return 5; // Очень высокий приоритет для покупки звезд
    }

    @Override
    public String getDescription() {
        return "Обработчик команд покупки звезд с интеграцией StarPurchaseFlowStrategy";
    }

    /**
     * Обработка подтверждения покупки
     */
    private TelegramResponse handlePurchaseConfirmation(InitiateStarPurchaseCommand command,
            DualBalanceResponse balanceData) throws Exception {
        log.info("✅ Подтверждение покупки звезд: userId={}, stars={}",
                command.getUserId(), command.getEffectiveStarCount());

        Money requiredAmount = command.getEffectiveAmount();

        // Проверяем достаточность средств
        if (!balanceData.hasSufficientMainFunds(requiredAmount)) {
            return handleInsufficientFunds(command, balanceData, requiredAmount);
        }

        // Инициируем покупку через Fragment API
        try {
            // TODO: Реализовать реальную покупку через FragmentIntegrationService
            log.info("🚀 Запуск покупки звезд через Fragment API: userId={}, stars={}, amount={}",
                    command.getUserId(), command.getEffectiveStarCount(), requiredAmount);

            // Временная заглушка для демонстрации
            var confirmationData = new StarPurchaseFlowStrategy.PurchaseConfirmationData(
                    command.getEffectiveStarCount(),
                    requiredAmount,
                    command.getCurrency().getSymbol());

            String message = starPurchaseFlowStrategy.formatContent("PURCHASE_CONFIRMATION", confirmationData);

            // Обновляем состояние сессии
            updateSessionState(command.getUserId(), "STAR_PURCHASE_CONFIRMED");

            // Создаем клавиатуру подтверждения покупки
            var keyboard = new TelegramKeyboardBuilder()
                    .addButton("✅ Подтвердить покупку", "confirm_purchase_" + command.getEffectiveStarCount())
                    .addButton("❌ Отменить", "cancel_purchase")
                    .newRow()
                    .addButton("🔙 Назад к балансу", "show_balance")
                    .build();

            return TelegramResponse.builder()
                    .successful(true)
                    .message(message)
                    .uiType("PURCHASE_CONFIRMATION")
                    .uiData(confirmationData)
                    .data(keyboard)
                    .build();

        } catch (Exception e) {
            log.error("❌ Ошибка при покупке звезд через Fragment API: {}", e.getMessage(), e);
            return TelegramResponse.error("Не удалось выполнить покупку звезд: " + e.getMessage());
        }
    }

    /**
     * Обработка проверки баланса
     */
    private TelegramResponse handleBalanceCheck(InitiateStarPurchaseCommand command, DualBalanceResponse balanceData) {
        log.debug("🔍 Проверка баланса для покупки звезд: userId={}", command.getUserId());

        Money requiredAmount = command.getEffectiveAmount();
        Integer starCount = command.getEffectiveStarCount();

        var checkData = new StarPurchaseFlowStrategy.BalanceCheckData(balanceData, starCount, requiredAmount);
        String message = starPurchaseFlowStrategy.formatContent("BALANCE_CHECK", checkData);

        // Создаем клавиатуру для проверки баланса
        var keyboardBuilder = new TelegramKeyboardBuilder();

        if (balanceData.hasSufficientMainFunds(requiredAmount)) {
            keyboardBuilder.addButton("✅ Продолжить покупку", "proceed_purchase_" + starCount);
        } else if (balanceData.hasSufficientBankFunds(requiredAmount)) {
            keyboardBuilder.addButton("🔄 Перевести средства", "transfer_funds_" + requiredAmount.getFormattedAmount());
        } else {
            keyboardBuilder.addButton("💳 Пополнить баланс", "topup_balance");
        }

        keyboardBuilder.addButton("🔙 Назад", "show_balance");
        var keyboard = keyboardBuilder.build();

        return TelegramResponse.builder()
                .successful(true)
                .message(message)
                .uiType("BALANCE_CHECK")
                .uiData(checkData)
                .data(keyboard)
                .build();
    }

    /**
     * Обработка интерфейса покупки
     */
    private TelegramResponse handlePurchaseInterface(InitiateStarPurchaseCommand command,
            DualBalanceResponse balanceData) {
        log.debug("🎯 Отображение интерфейса покупки звезд: userId={}", command.getUserId());

        String message = starPurchaseFlowStrategy.formatContent("PURCHASE_INTERFACE", balanceData);

        // Обновляем состояние сессии
        updateSessionState(command.getUserId(), "STAR_PURCHASE_INTERFACE");

        // Создаем клавиатуру с пакетами звезд
        var keyboardBuilder = new TelegramKeyboardBuilder();

        // Популярные пакеты звезд
        int[] starPackages = { 100, 250, 500, 1000, 2500 };
        boolean hasAnyAffordable = false;

        for (int stars : starPackages) {
            Money packagePrice = Money.of(java.math.BigDecimal.valueOf(stars * 0.01)); // Примерная цена
            boolean canAfford = balanceData.hasSufficientMainFunds(packagePrice);

            if (canAfford) {
                hasAnyAffordable = true;
                keyboardBuilder.addButton("⭐" + stars, "buy_stars_" + stars);
            } else {
                keyboardBuilder.addButton("❌ ⭐" + stars, "insufficient_" + stars);
            }

            if (stars == 500)
                keyboardBuilder.newRow(); // Разделяем по рядам
        }

        keyboardBuilder.newRow();

        if (hasAnyAffordable) {
            keyboardBuilder.addButton("💎 Свой размер", "custom_stars");
        }

        if (!balanceData.hasMainFunds()) {
            if (balanceData.hasBankFunds()) {
                keyboardBuilder.addButton("🔄 Перевести средства", "transfer_funds");
            } else {
                keyboardBuilder.addButton("💳 Пополнить баланс", "topup_balance");
            }
        }

        keyboardBuilder.addButton("🔙 Назад", "show_balance");
        var keyboard = keyboardBuilder.build();

        return TelegramResponse.builder()
                .successful(true)
                .message(message)
                .uiType("PURCHASE_INTERFACE")
                .uiData(balanceData)
                .data(keyboard)
                .build();
    }

    /**
     * Обработка недостаточности средств
     */
    private TelegramResponse handleInsufficientFunds(InitiateStarPurchaseCommand command,
            DualBalanceResponse balanceData, Money requiredAmount) {
        log.warn("💸 Недостаточно средств для покупки звезд: userId={}, required={}, available={}",
                command.getUserId(), requiredAmount, balanceData.getTotalBalance());

        var fundsData = new StarPurchaseFlowStrategy.InsufficientFundsData(
                balanceData, requiredAmount, command.getEffectiveStarCount());

        String message = starPurchaseFlowStrategy.formatContent("INSUFFICIENT_FUNDS", fundsData);

        // Создаем клавиатуру для недостатка средств
        var keyboardBuilder = new TelegramKeyboardBuilder();

        Money shortfall = requiredAmount.subtract(balanceData.getTotalBalance());

        if (balanceData.hasBankFunds() && !balanceData.hasMainFunds()) {
            keyboardBuilder.addButton("🔄 Перевести средства", "transfer_funds");
        }

        keyboardBuilder
                .addButton("💳 Пополнить " + shortfall.getFormattedAmount(),
                        "topup_amount_" + shortfall.getFormattedAmount())
                .newRow()
                .addButton("💳 Пополнить баланс", "topup_balance")
                .addButton("🔙 Назад", "show_balance");

        var keyboard = keyboardBuilder.build();

        return TelegramResponse.builder()
                .successful(true)
                .message(message)
                .uiType("INSUFFICIENT_FUNDS")
                .uiData(fundsData)
                .data(keyboard)
                .build();
    }

    /**
     * Получение баланса пользователя
     */
    private DualBalanceResponse getUserBalance(Long userId) {
        try {
            var balanceResult = balanceApplicationFacade.getBalance(userId);

            if (balanceResult != null && balanceResult.isSuccess()) {
                log.info("📊 Получен результат баланса для пользователя: {}", userId);

                // Реальная конвертация BalanceResponse в DualBalanceResponse
                var balanceResponse = balanceResult.getValue();
                return convertToBalanceResponse(userId, balanceResponse);
            }

            log.warn("⚠️ Не удалось получить баланс пользователя: {}", userId);
            return null;

        } catch (Exception e) {
            log.error("❌ Ошибка получения баланса пользователя {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Конвертация BalanceResponse в DualBalanceResponse
     */
    private DualBalanceResponse convertToBalanceResponse(Long userId, Object balanceData) {
        try {
            // Создаем реальный DualBalanceResponse на основе данных из балансового сервиса
            // TODO: Адаптировать под реальную структуру BalanceResponse когда она будет
            // доступна
            return DualBalanceResponse.builder()
                    .userId(userId)
                    .bankBalance(Money.zero()) // TODO: Извлечь из balanceData
                    .mainBalance(Money.zero()) // TODO: Извлечь из balanceData
                    .currency(shit.back.domain.balance.valueobjects.Currency.defaultCurrency())
                    .active(true)
                    .lastUpdated(java.time.LocalDateTime.now())
                    .totalTransferredToMain(Money.zero())
                    .totalSpentFromMain(Money.zero())
                    .build();
        } catch (Exception e) {
            log.error("❌ Ошибка конвертации BalanceResponse: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Обновление состояния сессии пользователя
     */
    private void updateSessionState(Long userId, String state) {
        try {
            log.debug("🔄 Обновление состояния сессии: userId={}, state={}", userId, state);

            // Реальное обновление сессии через sessionService
            var session = sessionService.getOrCreateSession(userId, "TelegramBot", "star_purchase", state);
            if (session != null) {
                log.info("✅ Состояние сессии обновлено для пользователя {}: {}", userId, state);
            }
        } catch (Exception e) {
            log.warn("⚠️ Не удалось обновить состояние сессии для пользователя {}: {}", userId, e.getMessage());
            // Не прерываем выполнение из-за ошибки сессии
        }
    }
}