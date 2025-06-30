package shit.back.telegram.commands.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.service.BalanceApplicationFacade;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.application.balance.mapper.BalanceResponseMapper;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.service.FragmentIntegrationService;
import shit.back.service.StarPurchaseService;
import shit.back.service.UserSessionUnifiedService;
import shit.back.telegram.commands.InitiateStarPurchaseCommand;
import shit.back.telegram.commands.TelegramCommandHandler;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.ui.strategy.StarPurchaseFlowStrategy;
import shit.back.telegram.ui.strategy.SimplifiedStarPurchaseStrategy;
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
    private SimplifiedStarPurchaseStrategy simplifiedStarPurchaseStrategy;

    @Autowired
    private StarPurchaseService starPurchaseService;

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

            // ФАЗА 2: Проверяем, можно ли использовать упрощенную архитектуру
            boolean useSimplified = shouldUseSimplifiedPurchase(command);

            if (useSimplified) {
                log.info("🌟 ФАЗА2: Использование упрощенной покупки звезд для пользователя {}", command.getUserId());
                return handleSimplifiedPurchase(command);
            } else {
                log.info("🔄 ФАЗА2: Использование legacy DualBalance покупки для пользователя {}", command.getUserId());
                return handleLegacyPurchase(command);
            }

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
            SimpleBalanceResponse balanceData) throws Exception {
        log.info("✅ Подтверждение покупки звезд: userId={}, stars={}",
                command.getUserId(), command.getEffectiveStarCount());

        Money requiredAmount = command.getEffectiveAmount();

        // Проверяем достаточность средств
        if (!balanceData.hasSufficientFunds(requiredAmount)) {
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
    private TelegramResponse handleBalanceCheck(InitiateStarPurchaseCommand command,
            SimpleBalanceResponse balanceData) {
        log.debug("🔍 Проверка баланса для покупки звезд: userId={}", command.getUserId());

        Money requiredAmount = command.getEffectiveAmount();
        Integer starCount = command.getEffectiveStarCount();

        var checkData = new StarPurchaseFlowStrategy.BalanceCheckData(balanceData, starCount, requiredAmount);
        String message = starPurchaseFlowStrategy.formatContent("BALANCE_CHECK", checkData);

        // Создаем клавиатуру для проверки баланса
        var keyboardBuilder = new TelegramKeyboardBuilder();

        if (balanceData.hasSufficientFunds(requiredAmount)) {
            keyboardBuilder.addButton("✅ Продолжить покупку", "proceed_purchase_" + starCount);
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
            SimpleBalanceResponse balanceData) {
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
            boolean canAfford = balanceData.hasSufficientFunds(packagePrice);

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

        if (!balanceData.getCurrentBalance().isPositive()) {
            keyboardBuilder.addButton("💳 Пополнить баланс", "topup_balance");
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
            SimpleBalanceResponse balanceData, Money requiredAmount) {
        log.warn("💸 Недостаточно средств для покупки звезд: userId={}, required={}, available={}",
                command.getUserId(), requiredAmount, balanceData.getCurrentBalance());

        var fundsData = new StarPurchaseFlowStrategy.InsufficientFundsData(
                balanceData, requiredAmount, command.getEffectiveStarCount());

        String message = starPurchaseFlowStrategy.formatContent("INSUFFICIENT_FUNDS", fundsData);

        // Создаем клавиатуру для недостатка средств
        var keyboardBuilder = new TelegramKeyboardBuilder();

        Money shortfall = requiredAmount.subtract(balanceData.getCurrentBalance());

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
    private SimpleBalanceResponse getUserBalance(Long userId) {
        try {
            var balanceResult = balanceApplicationFacade.getBalance(userId);

            if (balanceResult != null && balanceResult.isSuccess()) {
                log.info("📊 Получен результат баланса для пользователя: {}", userId);

                // Реальная конвертация BalanceResponse в SimpleBalanceResponse
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
     * Конвертация BalanceResponse в SimpleBalanceResponse
     *
     * ИСПРАВЛЕНО: Теперь использует BalanceResponseMapper для корректного
     * преобразования
     */
    private SimpleBalanceResponse convertToBalanceResponse(Long userId, Object balanceData) {
        try {
            // Используем исправленный BalanceResponseMapper для корректного преобразования
            if (balanceData instanceof shit.back.application.balance.dto.response.BalanceResponse) {
                var balanceResponse = (shit.back.application.balance.dto.response.BalanceResponse) balanceData;

                log.debug(
                        "🔄 ИСПРАВЛЕНО InitiateStarPurchase: Конвертация BalanceResponse через маппер для userId={}, currentBalance={}",
                        userId, balanceResponse.getCurrentBalance());

                // Используем исправленный маппер вместо создания нулевых балансов
                SimpleBalanceResponse dualResponse = shit.back.application.balance.mapper.BalanceResponseMapper
                        .toSimpleBalanceResponse(balanceResponse);

                if (dualResponse != null) {
                    log.info(
                            "✅ ИСПРАВЛЕНО InitiateStarPurchase: userId={}, конвертация успешна - currentBalance={}",
                            userId,
                            dualResponse.getFormattedBalance());
                    return dualResponse;
                }
            }

            // Fallback: создаем пустой баланс если конвертация не удалась
            log.warn(
                    "⚠️ InitiateStarPurchase: Неожиданный тип balanceData: {}, создаем пустой SimpleBalance для userId={}",
                    balanceData != null ? balanceData.getClass().getSimpleName() : "null", userId);

            return shit.back.application.balance.mapper.BalanceResponseMapper.createEmptyBalance(userId);

        } catch (Exception e) {
            log.error("❌ Ошибка конвертации BalanceResponse: {}", e.getMessage(), e);
            return shit.back.application.balance.mapper.BalanceResponseMapper.createEmptyBalance(userId);
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

    /**
     * ФАЗА 2: Определяет, следует ли использовать упрощенную покупку звезд
     */
    private boolean shouldUseSimplifiedPurchase(InitiateStarPurchaseCommand command) {
        // Простая логика: используем упрощенную архитектуру для прямых покупок
        return "PURCHASE_CONFIRMATION".equals(command.getOperationType()) ||
                "PURCHASE_INTERFACE".equals(command.getOperationType());
    }

    /**
     * ФАЗА 2: Обработка покупки звезд с упрощенной архитектурой
     */
    private TelegramResponse handleSimplifiedPurchase(InitiateStarPurchaseCommand command) {
        try {
            // Получаем простой баланс пользователя
            SimpleBalanceResponse simpleBalance = getSimpleBalance(command.getUserId());
            if (simpleBalance == null) {
                return TelegramResponse.error("Не удалось получить информацию о балансе");
            }

            // Обрабатываем различные типы операций упрощенным способом
            return switch (command.getOperationType()) {
                case "PURCHASE_CONFIRMATION" -> handleSimplifiedConfirmation(command, simpleBalance);
                case "PURCHASE_INTERFACE" -> handleSimplifiedInterface(simpleBalance);
                default ->
                    TelegramResponse.error("Неподдерживаемый тип упрощенной операции: " + command.getOperationType());
            };

        } catch (Exception e) {
            log.error("❌ ФАЗА2: Ошибка упрощенной покупки звезд: {}", e.getMessage(), e);
            return TelegramResponse.error("Ошибка при упрощенной покупке звезд: " + e.getMessage());
        }
    }

    /**
     * ФАЗА 2: Обработка покупки звезд со старой DualBalance архитектурой
     */
    private TelegramResponse handleLegacyPurchase(InitiateStarPurchaseCommand command) {
        try {
            // Получаем баланс пользователя
            SimpleBalanceResponse balanceData = getUserBalance(command.getUserId());
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

        } catch (Exception e) {
            log.error("❌ LEGACY: Ошибка legacy покупки звезд: {}", e.getMessage(), e);
            return TelegramResponse.error("Ошибка при legacy покупке звезд: " + e.getMessage());
        }
    }

    /**
     * ФАЗА 2: Получение простого баланса пользователя
     */
    private SimpleBalanceResponse getSimpleBalance(Long userId) {
        try {
            var balanceResult = balanceApplicationFacade.getBalance(userId);

            if (balanceResult != null && balanceResult.isSuccess()) {
                log.debug("🌟 ФАЗА2: Получен результат баланса для пользователя: {}", userId);

                if (balanceResult.getValue() instanceof shit.back.application.balance.dto.response.BalanceResponse) {
                    var balanceResponse = (shit.back.application.balance.dto.response.BalanceResponse) balanceResult
                            .getValue();
                    return BalanceResponseMapper.toSimpleBalanceResponse(balanceResponse);
                }
            }

            log.warn("⚠️ ФАЗА2: Не удалось получить простой баланс пользователя: {}", userId);
            return BalanceResponseMapper.createEmptyBalance(userId);

        } catch (Exception e) {
            log.error("❌ ФАЗА2: Ошибка получения простого баланса пользователя {}: {}", userId, e.getMessage(), e);
            return BalanceResponseMapper.createEmptyBalance(userId);
        }
    }

    /**
     * ФАЗА 2: Упрощенное подтверждение покупки звезд
     */
    private TelegramResponse handleSimplifiedConfirmation(InitiateStarPurchaseCommand command,
            SimpleBalanceResponse balance) {
        try {
            Money requiredAmount = command.getEffectiveAmount();
            Integer starCount = command.getEffectiveStarCount();

            // Проверяем достаточность средств
            if (!balance.hasSufficientFunds(requiredAmount)) {
                log.warn("💸 ФАЗА2: Недостаточно средств для покупки {} звезд: требуется {}, доступно {}",
                        starCount, requiredAmount.getFormattedAmount(), balance.getFormattedBalance());
                return TelegramResponse.error("Недостаточно средств для покупки звезд");
            }

            // Выполняем прямую покупку через StarPurchaseService
            var purchaseResult = starPurchaseService.purchaseStars(command.getUserId(), starCount, requiredAmount);

            if (purchaseResult.isSuccess()) {
                log.info("✅ ФАЗА2: Упрощенная покупка звезд успешна: userId={}, stars={}, transactionId={}",
                        command.getUserId(), starCount, purchaseResult.getTransactionId());

                String successMessage = String.format(
                        "🌟 <b>Покупка звезд завершена!</b>\n\n" +
                                "⭐ Куплено звезд: <b>%d</b>\n" +
                                "💰 Списано с баланса: <b>%s</b>\n" +
                                "🔢 ID транзакции: <code>%s</code>\n\n" +
                                "✅ Звезды добавлены в ваш аккаунт Telegram!",
                        starCount,
                        requiredAmount.getFormattedAmount() + " " + balance.getCurrency().getSymbol(),
                        purchaseResult.getTransactionId());

                // Создаем простую клавиатуру
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
            } else {
                log.error("❌ ФАЗА2: Ошибка упрощенной покупки: {}", purchaseResult.getErrorMessage());
                return TelegramResponse.error("Не удалось выполнить покупку: " + purchaseResult.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("❌ ФАЗА2: Критическая ошибка упрощенного подтверждения: {}", e.getMessage(), e);
            return TelegramResponse.error("Произошла ошибка при покупке звезд: " + e.getMessage());
        }
    }

    /**
     * ФАЗА 2: Упрощенный интерфейс покупки звезд
     */
    private TelegramResponse handleSimplifiedInterface(SimpleBalanceResponse balance) {
        try {
            // Используем упрощенную стратегию для создания интерфейса
            var uiResponse = simplifiedStarPurchaseStrategy.createStarPurchaseFlow(balance);

            // Конвертируем TelegramUIResponse в TelegramResponse
            return TelegramResponse.builder()
                    .successful(true)
                    .message(uiResponse.getMessageText())
                    .uiType("SIMPLIFIED_PURCHASE_INTERFACE")
                    .uiData(balance)
                    .data(uiResponse.getKeyboard())
                    .build();

        } catch (Exception e) {
            log.error("❌ ФАЗА2: Ошибка создания упрощенного интерфейса: {}", e.getMessage(), e);
            return TelegramResponse.error("Ошибка при создании интерфейса покупки: " + e.getMessage());
        }
    }
}