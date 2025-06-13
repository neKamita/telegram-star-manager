package shit.back.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import shit.back.model.UserSession;
import shit.back.model.Order;
import shit.back.model.StarPackage;
import shit.back.security.RateLimitService;
import shit.back.security.SecurityValidator;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.UserBalanceEntity;
import shit.back.exception.BalanceException;
import shit.back.exception.InsufficientBalanceException;
import shit.back.service.BalanceService;
import shit.back.service.BalanceTransactionService;
import shit.back.service.OrderService;
import shit.back.service.PaymentService;
import shit.back.service.PriceService;
import shit.back.service.UserSessionService;
import shit.back.utils.MessageUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class CallbackHandler {

    @Autowired
    private UserSessionService userSessionService;

    @Autowired
    private PriceService priceService;

    @Autowired
    private SecurityValidator securityValidator;

    @Autowired
    private RateLimitService rateLimitService;

    // === ИНТЕГРАЦИЯ С БАЛАНСОМ ===
    @Autowired
    private BalanceService balanceService;

    @Autowired
    private BalanceTransactionService balanceTransactionService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    public EditMessageText handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        User user = callbackQuery.getFrom();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        log.info("🔘 CallbackHandler: обработка callback от {} (ID: {}): {}",
                user.getFirstName(), user.getId(), data);

        // Валидация callback данных
        SecurityValidator.ValidationResult validationResult = securityValidator.validateCallbackData(data);
        if (!validationResult.isValid()) {
            log.warn("❌ Невалидные callback данные от пользователя {}: {}", user.getId(),
                    validationResult.getErrorMessage());
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Некорректный запрос. Попробуйте еще раз или используйте /start");
        }

        // Проверка rate limit для пользователя
        RateLimitService.RateLimitResult rateLimitResult = rateLimitService.checkUserLimit(user.getId());
        if (!rateLimitResult.isAllowed()) {
            log.warn("⏰ Rate limit превышен для пользователя {}: {}", user.getId(), rateLimitResult.getErrorMessage());
            return MessageUtils.createEditMessage(chatId, messageId,
                    "⏰ Слишком много запросов. Подождите немного и попробуйте снова.");
        }

        // Валидация данных пользователя
        SecurityValidator.ValidationResult usernameValidation = securityValidator.validateUsername(user.getUserName());
        if (!usernameValidation.isValid()) {
            log.warn("⚠️ Невалидное имя пользователя от user {}: {}", user.getId(),
                    usernameValidation.getErrorMessage());
            // Продолжаем работу, но логируем предупреждение
        }

        // Получаем сессию пользователя
        UserSession session = userSessionService.getOrCreateSession(
                user.getId(),
                securityValidator.sanitizeText(user.getUserName()),
                securityValidator.sanitizeText(user.getFirstName()),
                securityValidator.sanitizeText(user.getLastName()));

        log.info("👤 Состояние сессии пользователя: {}", session.getState());

        EditMessageText response = handleCallbackData(data, chatId, messageId, session);
        log.info("📝 CallbackHandler: ответ подготовлен для chatId {}", chatId);

        return response;
    }

    private EditMessageText handleCallbackData(String data, Long chatId, Integer messageId, UserSession session) {
        if (data.equals("buy_stars")) {
            return handleBuyStars(chatId, messageId, session);
        }

        if (data.equals("show_prices")) {
            return handleShowPrices(chatId, messageId, session);
        }

        if (data.equals("help")) {
            return handleHelp(chatId, messageId);
        }

        if (data.equals("my_orders")) {
            return handleMyOrders(chatId, messageId, session);
        }

        if (data.equals("back_to_main")) {
            return handleBackToMain(chatId, messageId, session);
        }

        if (data.startsWith("select_package_")) {
            String packageId = data.replace("select_package_", "");
            return handlePackageSelection(chatId, messageId, session, packageId);
        }

        if (data.startsWith("confirm_order_")) {
            String orderId = data.replace("confirm_order_", "");
            return handleOrderConfirmation(chatId, messageId, session, orderId);
        }

        if (data.equals("cancel_order")) {
            return handleCancelOrder(chatId, messageId, session);
        }

        if (data.startsWith("cancel_order_")) {
            String orderId = data.replace("cancel_order_", "");
            return handleCancelSpecificOrder(chatId, messageId, session, orderId);
        }

        if (data.startsWith("pay_ton_")) {
            String orderId = data.replace("pay_ton_", "");
            return handleTonPayment(chatId, messageId, session, orderId);
        }

        if (data.startsWith("pay_crypto_")) {
            String orderId = data.replace("pay_crypto_", "");
            return handleCryptoPayment(chatId, messageId, session, orderId);
        }

        if (data.startsWith("check_payment_")) {
            String orderId = data.replace("check_payment_", "");
            return handleCheckPayment(chatId, messageId, session, orderId);
        }

        // === НОВЫЕ CALLBACK'Ы БАЛАНСА ===

        if (data.equals("show_balance")) {
            return handleShowBalance(chatId, messageId, session);
        }

        if (data.equals("topup_balance_menu")) {
            return handleTopupBalanceMenu(chatId, messageId, session);
        }

        if (data.startsWith("topup_balance_")) {
            String amount = data.replace("topup_balance_", "");
            return handleTopupBalanceAmount(chatId, messageId, session, amount);
        }

        if (data.equals("show_balance_history")) {
            return handleShowBalanceHistory(chatId, messageId, session);
        }

        if (data.equals("refresh_balance_history")) {
            return handleRefreshBalanceHistory(chatId, messageId, session);
        }

        if (data.equals("back_to_balance")) {
            return handleBackToBalance(chatId, messageId, session);
        }

        if (data.startsWith("balance_payment_")) {
            String orderId = data.replace("balance_payment_", "");
            return handleBalancePayment(chatId, messageId, session, orderId);
        }

        if (data.startsWith("mixed_payment_")) {
            String orderId = data.replace("mixed_payment_", "");
            return handleMixedPayment(chatId, messageId, session, orderId);
        }

        if (data.equals("export_balance_history")) {
            return handleExportBalanceHistory(chatId, messageId, session);
        }

        if (data.equals("topup_balance_custom")) {
            return handleCustomTopupAmount(chatId, messageId, session);
        }

        // === НОВЫЕ CALLBACK'Ы ДЛЯ ПЛАТЕЖНЫХ СИСТЕМ ===

        if (data.startsWith("topup_ton_")) {
            String amount = data.replace("topup_ton_", "");
            return handleTopupTon(chatId, messageId, session, amount);
        }

        if (data.startsWith("topup_yookassa_")) {
            String amount = data.replace("topup_yookassa_", "");
            return handleTopupYooKassa(chatId, messageId, session, amount);
        }

        if (data.startsWith("topup_qiwi_")) {
            String amount = data.replace("topup_qiwi_", "");
            return handleTopupQiwi(chatId, messageId, session, amount);
        }

        if (data.startsWith("topup_sberpay_")) {
            String amount = data.replace("topup_sberpay_", "");
            return handleTopupSberPay(chatId, messageId, session, amount);
        }

        // Неизвестный callback
        return MessageUtils.createEditMessage(chatId, messageId,
                "❓ Неизвестная команда. Попробуйте еще раз или используйте /start");
    }

    private EditMessageText handleBuyStars(Long chatId, Integer messageId, UserSession session) {
        userSessionService.updateSessionState(session.getUserId(), UserSession.SessionState.SELECTING_PACKAGE);

        String text = "💰 <b>Выберите пакет Telegram Stars:</b>\n\n" +
                MessageUtils.formatPricesMessage(priceService.getAllPackages());

        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                MessageUtils.createPackageSelectionKeyboard(priceService.getAllPackages()));
    }

    private EditMessageText handleShowPrices(Long chatId, Integer messageId, UserSession session) {
        String text = MessageUtils.formatPricesMessage(priceService.getAllPackages());
        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                MessageUtils.createPackageSelectionKeyboard(priceService.getAllPackages()));
    }

    private EditMessageText handleHelp(Long chatId, Integer messageId) {
        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId,
                MessageUtils.formatHelpMessage(), MessageUtils.createHelpKeyboard());
    }

    private EditMessageText handleMyOrders(Long chatId, Integer messageId, UserSession session) {
        Optional<Order> activeOrder = userSessionService.getUserActiveOrder(session.getUserId());

        if (activeOrder.isPresent()) {
            Order order = activeOrder.get();
            String text = String.format("""
                    📋 <b>Ваши заказы:</b>

                    %s <b>Заказ %s</b>
                    🎯 Статус: %s
                    ⭐ Звезды: %d
                    💰 Сумма: $%.2f
                    📅 Создан: %s
                    """,
                    order.getStatusEmoji(),
                    order.getFormattedOrderId(),
                    order.getStatus().name(),
                    order.getStarPackage().getStars(),
                    order.getAmount(),
                    order.getCreatedAt().toLocalDate());

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                    MessageUtils.createMyOrdersKeyboard());
        } else {
            String text = "📋 У вас пока нет заказов.\n\n💰 Сделайте первый заказ прямо сейчас!";
            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                    MessageUtils.createMyOrdersKeyboard());
        }
    }

    private EditMessageText handleBackToMain(Long chatId, Integer messageId, UserSession session) {
        userSessionService.updateSessionState(session.getUserId(), UserSession.SessionState.IDLE);

        String welcomeText = MessageUtils.formatWelcomeMessage(session.getDisplayName());
        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, welcomeText,
                MessageUtils.createMainMenuKeyboard());
    }

    private EditMessageText handlePackageSelection(Long chatId, Integer messageId, UserSession session,
            String packageId) {
        // Валидация package ID
        SecurityValidator.ValidationResult packageValidation = securityValidator.validatePackageId(packageId);
        if (!packageValidation.isValid()) {
            log.warn("Invalid package ID from user {}: {}", session.getUserId(), packageValidation.getErrorMessage());
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Некорректный пакет. Попробуйте выбрать из списка.");
        }

        Optional<StarPackage> packageOpt = priceService.getPackageById(packageId);

        if (packageOpt.isPresent()) {
            StarPackage starPackage = packageOpt.get();
            userSessionService.setSelectedPackage(session.getUserId(), starPackage);

            // Создаем заказ
            Order order = userSessionService.createOrder(session.getUserId());

            if (order != null) {
                log.info("Order created for user {}: {} stars for ${}",
                        session.getUserId(), starPackage.getStars(), starPackage.getDiscountedPrice());
                String text = MessageUtils.formatOrderConfirmation(order);
                return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                        MessageUtils.createOrderConfirmationKeyboard(order.getOrderId()));
            }
        }

        return MessageUtils.createEditMessage(chatId, messageId,
                "❌ Ошибка при создании заказа. Попробуйте еще раз.");
    }

    private EditMessageText handleOrderConfirmation(Long chatId, Integer messageId, UserSession session,
            String orderId) {
        // Валидация order ID
        SecurityValidator.ValidationResult orderValidation = securityValidator.validateOrderId(orderId);
        if (!orderValidation.isValid()) {
            log.warn("Invalid order ID from user {}: {}", session.getUserId(), orderValidation.getErrorMessage());
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Некорректный заказ. Попробуйте создать новый заказ.");
        }

        Optional<Order> orderOpt = userSessionService.getOrder(orderId);

        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            userSessionService.updateOrderStatus(orderId, Order.OrderStatus.AWAITING_PAYMENT);
            userSessionService.updateSessionState(session.getUserId(), UserSession.SessionState.AWAITING_PAYMENT);

            log.info("Order confirmed for user {}: {}", session.getUserId(), orderId);
            String text = MessageUtils.formatPaymentMessage(order);
            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                    MessageUtils.createPaymentKeyboard(orderId));
        }

        return MessageUtils.createEditMessage(chatId, messageId,
                "❌ Заказ не найден. Попробуйте создать новый заказ.");
    }

    private EditMessageText handleCancelOrder(Long chatId, Integer messageId, UserSession session) {
        userSessionService.clearUserSession(session.getUserId());

        String welcomeText = MessageUtils.formatWelcomeMessage(session.getDisplayName());
        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, welcomeText,
                MessageUtils.createMainMenuKeyboard());
    }

    private EditMessageText handleCancelSpecificOrder(Long chatId, Integer messageId, UserSession session,
            String orderId) {
        userSessionService.updateOrderStatus(orderId, Order.OrderStatus.CANCELLED);
        userSessionService.clearUserSession(session.getUserId());

        String text = "❌ Заказ отменен.\n\n🏠 Возвращайтесь в главное меню или создайте новый заказ.";
        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                MessageUtils.createBackToMainKeyboard());
    }

    private EditMessageText handleTonPayment(Long chatId, Integer messageId, UserSession session, String orderId) {
        // Заглушка для TON платежа
        String text = """
                💎 <b>Оплата через TON Wallet</b>

                🚧 <i>Интеграция с TON Wallet находится в разработке</i>

                📞 Свяжитесь с поддержкой: @support_bot
                """;
        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                MessageUtils.createBackToMainKeyboard());
    }

    private EditMessageText handleCryptoPayment(Long chatId, Integer messageId, UserSession session, String orderId) {
        // Заглушка для крипто-платежа
        String text = """
                ₿ <b>Криптоплатеж</b>

                🚧 <i>Интеграция с криптоплатежами находится в разработке</i>

                📞 Свяжитесь с поддержкой: @support_bot
                """;
        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                MessageUtils.createBackToMainKeyboard());
    }

    private EditMessageText handleCheckPayment(Long chatId, Integer messageId, UserSession session, String orderId) {
        // Заглушка для проверки платежа
        String text = """
                🔄 <b>Проверка платежа...</b>

                🚧 <i>Автоматическая проверка платежей находится в разработке</i>

                ⏳ Платеж будет проверен вручную в течение 5-10 минут
                📞 Поддержка: @support_bot
                """;
        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                MessageUtils.createBackToMainKeyboard());
    }

    // ============================================
    // === НОВЫЕ ОБРАБОТЧИКИ БАЛАНСА ===
    // ============================================

    /**
     * Обработка меню пополнения баланса
     */
    private EditMessageText handleTopupBalanceMenu(Long chatId, Integer messageId, UserSession session) {
        log.info("💳 Обработка меню пополнения баланса для пользователя: {}", session.getUserId());

        try {
            userSessionService.updateSessionState(session.getUserId(), UserSession.SessionState.TOPPING_UP_BALANCE);
            UserBalanceEntity balance = balanceService.getOrCreateBalance(session.getUserId());
            String topupMessage = MessageUtils.createTopupMessage(balance);

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, topupMessage,
                    MessageUtils.createTopupKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при открытии меню пополнения для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при открытии меню пополнения. Попробуйте позже.");
        }
    }

    /**
     * Обработка выбора суммы пополнения
     */
    private EditMessageText handleTopupBalanceAmount(Long chatId, Integer messageId, UserSession session,
            String amountStr) {
        log.info("💰 Обработка пополнения баланса для пользователя {}: сумма={}",
                session.getUserId(), amountStr);

        try {
            BigDecimal amount;
            String amountText;

            // Парсим сумму
            switch (amountStr) {
                case "100" -> {
                    amount = new BigDecimal("100.00");
                    amountText = "100 ₽";
                }
                case "500" -> {
                    amount = new BigDecimal("500.00");
                    amountText = "500 ₽";
                }
                case "1000" -> {
                    amount = new BigDecimal("1000.00");
                    amountText = "1000 ₽";
                }
                case "2000" -> {
                    amount = new BigDecimal("2000.00");
                    amountText = "2000 ₽";
                }
                default -> {
                    return MessageUtils.createEditMessage(chatId, messageId,
                            "❌ Некорректная сумма пополнения. Выберите из предложенных вариантов.");
                }
            }

            // Обновляем состояние сессии
            userSessionService.updateSessionState(session.getUserId(), UserSession.SessionState.SELECTING_PAYMENT_TYPE);

            UserBalanceEntity balance = balanceService.getOrCreateBalance(session.getUserId());

            String text = String.format("""
                    💳 <b>Пополнение баланса</b>

                    💰 <b>Сумма пополнения:</b> %s
                    💵 <b>Текущий баланс:</b> %.2f ₽

                    🔒 <b>Выберите способ оплаты:</b>

                    <i>⚠️ После пополнения средства сразу станут доступны для покупок</i>
                    """,
                    amountText,
                    balance.getCurrentBalance());

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                    createTopupPaymentKeyboard(amountStr));

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке суммы пополнения для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при обработке пополнения. Попробуйте позже.");
        }
    }

    /**
     * Показать историю транзакций баланса
     */
    private EditMessageText handleShowBalanceHistory(Long chatId, Integer messageId, UserSession session) {
        log.info("📊 Показ истории баланса для пользователя: {}", session.getUserId());

        try {
            List<BalanceTransactionEntity> history = balanceService.getBalanceHistory(session.getUserId(), 10);
            String historyMessage = MessageUtils.createBalanceHistoryMessage(history, session.getDisplayName());

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, historyMessage,
                    MessageUtils.createBalanceHistoryKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при получении истории для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при получении истории транзакций. Попробуйте позже.");
        }
    }

    /**
     * Обновление истории транзакций
     */
    private EditMessageText handleRefreshBalanceHistory(Long chatId, Integer messageId, UserSession session) {
        log.info("🔄 Обновление истории баланса для пользователя: {}", session.getUserId());
        return handleShowBalanceHistory(chatId, messageId, session);
    }

    /**
     * Возврат к балансу
     */
    private EditMessageText handleBackToBalance(Long chatId, Integer messageId, UserSession session) {
        log.info("🔙 Возврат к балансу для пользователя: {}", session.getUserId());

        try {
            userSessionService.updateSessionState(session.getUserId(), UserSession.SessionState.IDLE);
            UserBalanceEntity balance = balanceService.getOrCreateBalance(session.getUserId());
            String balanceMessage = MessageUtils.createBalanceInfoMessage(balance, session.getDisplayName());

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, balanceMessage,
                    MessageUtils.createBalanceMenuKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при возврате к балансу для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при загрузке баланса. Попробуйте позже.");
        }
    }

    /**
     * Обработка оплаты балансом
     */
    private EditMessageText handleBalancePayment(Long chatId, Integer messageId, UserSession session, String orderId) {
        log.info("💸 Обработка оплаты балансом для пользователя {}: заказ={}",
                session.getUserId(), orderId);

        try {
            // Проверяем существование заказа
            Optional<Order> orderOpt = userSessionService.getOrder(orderId);
            if (orderOpt.isEmpty()) {
                return MessageUtils.createEditMessage(chatId, messageId,
                        "❌ Заказ не найден. Попробуйте создать новый заказ.");
            }

            Order order = orderOpt.get();
            BigDecimal orderAmount = order.getAmount();

            // Проверяем достаточность средств
            boolean sufficientFunds = balanceService.checkSufficientBalance(session.getUserId(), orderAmount);
            if (!sufficientFunds) {
                UserBalanceEntity balance = balanceService.getOrCreateBalance(session.getUserId());
                BigDecimal shortage = orderAmount.subtract(balance.getCurrentBalance());

                String text = String.format("""
                        ❌ <b>Недостаточно средств на балансе</b>

                        💰 <b>Сумма заказа:</b> %.2f ₽
                        💵 <b>Ваш баланс:</b> %.2f ₽
                        💸 <b>Не хватает:</b> %.2f ₽

                        💡 Пополните баланс или выберите другой способ оплаты
                        """,
                        orderAmount, balance.getCurrentBalance(), shortage);

                return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                        MessageUtils.createPaymentKeyboard(orderId));
            }

            // Обновляем состояние
            userSessionService.updateSessionState(session.getUserId(),
                    UserSession.SessionState.BALANCE_PAYMENT_PROCESSING);

            // Обрабатываем платеж
            BalanceTransactionEntity transaction = balanceService.processBalancePayment(
                    session.getUserId(), orderId, orderAmount);

            // Обновляем статус заказа
            userSessionService.updateOrderStatus(orderId, Order.OrderStatus.COMPLETED);
            userSessionService.updateSessionState(session.getUserId(), UserSession.SessionState.COMPLETED);

            String text = String.format("""
                    ✅ <b>Оплата успешно выполнена!</b>

                    🎉 <b>Заказ %s оплачен</b>
                    ⭐ <b>Звезды:</b> %d
                    💰 <b>Сумма:</b> %.2f ₽
                    💳 <b>Способ оплаты:</b> Баланс
                    🆔 <b>Транзакция:</b> <code>%s</code>

                    🚀 <i>Звезды будут зачислены в течение 5-10 минут</i>
                    """,
                    order.getFormattedOrderId(),
                    order.getStarPackage().getStars(),
                    orderAmount,
                    transaction.getTransactionId());

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                    MessageUtils.createBackToMainKeyboard());

        } catch (InsufficientBalanceException e) {
            log.warn("💸 Недостаточно средств для пользователя {}: {}", session.getUserId(), e.getMessage());
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Недостаточно средств на балансе. Пополните баланс или выберите другой способ оплаты.");
        } catch (Exception e) {
            log.error("❌ Ошибка при оплате балансом для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при обработке платежа. Попробуйте позже или выберите другой способ оплаты.");
        }
    }

    /**
     * Обработка смешанной оплаты (частично баланс + внешний платеж)
     */
    private EditMessageText handleMixedPayment(Long chatId, Integer messageId, UserSession session, String orderId) {
        log.info("🔄 Обработка смешанной оплаты для пользователя {}: заказ={}",
                session.getUserId(), orderId);

        // Пока заглушка для смешанной оплаты
        String text = """
                🔄 <b>Смешанная оплата</b>

                🚧 <i>Функция смешанной оплаты находится в разработке</i>

                💡 Пополните баланс или выберите полную оплату другим способом
                📞 Поддержка: @support_bot
                """;

        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                MessageUtils.createPaymentKeyboard(orderId));
    }

    /**
     * Экспорт истории баланса (заглушка)
     */
    private EditMessageText handleExportBalanceHistory(Long chatId, Integer messageId, UserSession session) {
        log.info("📄 Запрос экспорта истории для пользователя: {}", session.getUserId());

        String text = """
                📄 <b>Экспорт истории транзакций</b>

                🚧 <i>Функция экспорта находится в разработке</i>

                💡 Скоро вы сможете экспортировать историю в PDF или Excel
                📞 Поддержка: @support_bot
                """;

        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                MessageUtils.createBalanceHistoryKeyboard());
    }

    /**
     * Пользовательская сумма пополнения (заглушка)
     */
    private EditMessageText handleCustomTopupAmount(Long chatId, Integer messageId, UserSession session) {
        log.info("✏️ Запрос пользовательской суммы для пользователя: {}", session.getUserId());

        String text = """
                ✏️ <b>Пользовательская сумма</b>

                🚧 <i>Функция ввода произвольной суммы находится в разработке</i>

                💡 Пока выберите одну из предложенных сумм
                📞 Поддержка: @support_bot
                """;

        return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                MessageUtils.createTopupKeyboard());
    }

    /**
     * Создание клавиатуры для выбора способа оплаты пополнения
     */
    private InlineKeyboardMarkup createTopupPaymentKeyboard(String amount) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Способы оплаты
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("💎 TON Wallet", "topup_ton_" + amount));
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("💳 YooKassa", "topup_yookassa_" + amount));
        rows.add(row2);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createButton("🥝 Qiwi", "topup_qiwi_" + amount));
        rows.add(row3);

        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(createButton("🏦 SberPay", "topup_sberpay_" + amount));
        rows.add(row4);

        // Назад
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        row5.add(createButton("🔙 Выбрать другую сумму", "topup_balance_menu"));
        rows.add(row5);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Форматирование сообщения оплаты с учетом баланса
     */
    private String formatPaymentMessageWithBalance(Order order, UserBalanceEntity balance) {
        BigDecimal orderAmount = order.getAmount();
        BigDecimal userBalance = balance.getCurrentBalance();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                💳 <b>Оплата заказа %s</b>

                ⭐ <b>Звезды:</b> %d
                💰 <b>Сумма заказа:</b> %.2f ₽
                💵 <b>Ваш баланс:</b> %.2f ₽
                """,
                order.getFormattedOrderId(),
                order.getStarPackage().getStars(),
                orderAmount,
                userBalance));

        // Анализируем возможности оплаты
        if (userBalance.compareTo(orderAmount) >= 0) {
            sb.append("\n✅ <b>Достаточно средств для оплаты балансом!</b>");
            sb.append("\n💡 <i>Рекомендуем оплатить балансом - это быстро и безопасно</i>");
        } else if (userBalance.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remaining = orderAmount.subtract(userBalance);
            sb.append(String.format("\n⚠️ <b>Недостаточно средств на балансе</b>"));
            sb.append(String.format("\n💸 <b>Не хватает:</b> %.2f ₽", remaining));
            sb.append("\n💡 <i>Можете доплатить внешним способом или пополнить баланс</i>");
        } else {
            sb.append("\n💳 <b>Выберите способ оплаты:</b>");
            sb.append("\n💡 <i>Рекомендуем пополнить баланс для быстрых покупок</i>");
        }

        sb.append("\n\n🔒 <i>Все платежи защищены и обрабатываются безопасно</i>");
        return sb.toString();
    }

    /**
     * Создание кнопки
     */
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    // ============================================
    // === ОБРАБОТЧИКИ ПЛАТЕЖНЫХ СИСТЕМ ===
    // ============================================

    /**
     * Обработка пополнения через TON Wallet
     */
    private EditMessageText handleTopupTon(Long chatId, Integer messageId, UserSession session, String amountStr) {
        log.info("💎 Обработка пополнения через TON для пользователя {}: сумма={}",
                session.getUserId(), amountStr);

        try {
            BigDecimal amount = parseAmount(amountStr);
            if (amount == null) {
                return MessageUtils.createEditMessage(chatId, messageId,
                        "❌ Некорректная сумма пополнения.");
            }

            // Создаем платежную ссылку через PaymentService
            String paymentUrl = paymentService.processPayment(session.getUserId(), amount, "TON");

            String text = String.format("""
                    💎 <b>Пополнение через TON Wallet</b>

                    💰 <b>Сумма пополнения:</b> %.2f ₽
                    🔗 <b>Ссылка для оплаты:</b> <a href="%s">Перейти к оплате</a>

                    ⏱️ <b>Время действия ссылки:</b> 30 минут
                    🔄 <b>Автоматическое зачисление</b> после подтверждения

                    <i>💡 Нажмите на ссылку и завершите оплату в TON Wallet</i>
                    """,
                    amount, paymentUrl);

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                    MessageUtils.createBackToMainKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при создании TON платежа для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при создании платежа. Попробуйте позже или выберите другой способ оплаты.");
        }
    }

    /**
     * Обработка пополнения через YooKassa
     */
    private EditMessageText handleTopupYooKassa(Long chatId, Integer messageId, UserSession session, String amountStr) {
        log.info("💳 Обработка пополнения через YooKassa для пользователя {}: сумма={}",
                session.getUserId(), amountStr);

        try {
            BigDecimal amount = parseAmount(amountStr);
            if (amount == null) {
                return MessageUtils.createEditMessage(chatId, messageId,
                        "❌ Некорректная сумма пополнения.");
            }

            // Создаем платежную ссылку через PaymentService
            String paymentUrl = paymentService.processPayment(session.getUserId(), amount, "YooKassa");

            String text = String.format("""
                    💳 <b>Пополнение через YooKassa</b>

                    💰 <b>Сумма пополнения:</b> %.2f ₽
                    🔗 <b>Ссылка для оплаты:</b> <a href="%s">Перейти к оплате</a>

                    💳 <b>Способы оплаты:</b> Банковские карты, ЮMoney, SberPay
                    ⏱️ <b>Время действия ссылки:</b> 30 минут
                    🔄 <b>Автоматическое зачисление</b> после подтверждения

                    <i>💡 Нажмите на ссылку и выберите удобный способ оплаты</i>
                    """,
                    amount, paymentUrl);

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                    MessageUtils.createBackToMainKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при создании YooKassa платежа для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при создании платежа. Попробуйте позже или выберите другой способ оплаты.");
        }
    }

    /**
     * Обработка пополнения через Qiwi
     */
    private EditMessageText handleTopupQiwi(Long chatId, Integer messageId, UserSession session, String amountStr) {
        log.info("🥝 Обработка пополнения через Qiwi для пользователя {}: сумма={}",
                session.getUserId(), amountStr);

        try {
            BigDecimal amount = parseAmount(amountStr);
            if (amount == null) {
                return MessageUtils.createEditMessage(chatId, messageId,
                        "❌ Некорректная сумма пополнения.");
            }

            // Создаем платежную ссылку через PaymentService
            String paymentUrl = paymentService.processPayment(session.getUserId(), amount, "Qiwi");

            String text = String.format("""
                    🥝 <b>Пополнение через Qiwi</b>

                    💰 <b>Сумма пополнения:</b> %.2f ₽
                    🔗 <b>Ссылка для оплаты:</b> <a href="%s">Перейти к оплате</a>

                    💳 <b>Способы оплаты:</b> Qiwi Кошелек, банковские карты
                    ⏱️ <b>Время действия ссылки:</b> 60 минут
                    🔄 <b>Автоматическое зачисление</b> после подтверждения

                    <i>💡 Нажмите на ссылку и завершите оплату</i>
                    """,
                    amount, paymentUrl);

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                    MessageUtils.createBackToMainKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при создании Qiwi платежа для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при создании платежа. Попробуйте позже или выберите другой способ оплаты.");
        }
    }

    /**
     * Обработка пополнения через SberPay
     */
    private EditMessageText handleTopupSberPay(Long chatId, Integer messageId, UserSession session, String amountStr) {
        log.info("🏦 Обработка пополнения через SberPay для пользователя {}: сумма={}",
                session.getUserId(), amountStr);

        try {
            BigDecimal amount = parseAmount(amountStr);
            if (amount == null) {
                return MessageUtils.createEditMessage(chatId, messageId,
                        "❌ Некорректная сумма пополнения.");
            }

            // Создаем платежную ссылку через PaymentService
            String paymentUrl = paymentService.processPayment(session.getUserId(), amount, "SberPay");

            String text = String.format("""
                    🏦 <b>Пополнение через SberPay</b>

                    💰 <b>Сумма пополнения:</b> %.2f ₽
                    🔗 <b>Ссылка для оплаты:</b> <a href="%s">Перейти к оплате</a>

                    💳 <b>Способы оплаты:</b> SberPay, банковские карты Сбербанка
                    ⏱️ <b>Время действия ссылки:</b> 30 минут
                    🔄 <b>Автоматическое зачисление</b> после подтверждения

                    <i>💡 Нажмите на ссылку и завершите оплату в SberPay</i>
                    """,
                    amount, paymentUrl);

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, text,
                    MessageUtils.createBackToMainKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при создании SberPay платежа для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при создании платежа. Попробуйте позже или выберите другой способ оплаты.");
        }
    }

    /**
     * Парсинг суммы из строки
     */
    private BigDecimal parseAmount(String amountStr) {
        try {
            return switch (amountStr) {
                case "100" -> new BigDecimal("100.00");
                case "500" -> new BigDecimal("500.00");
                case "1000" -> new BigDecimal("1000.00");
                case "2000" -> new BigDecimal("2000.00");
                default -> null;
            };
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при парсинге суммы: {}", amountStr);
            return null;
        }
    }

    /**
     * Обработка показа баланса пользователя
     */
    private EditMessageText handleShowBalance(Long chatId, Integer messageId, UserSession session) {
        log.info("💰 Показ баланса для пользователя: {}", session.getUserId());

        try {
            UserBalanceEntity balance = balanceService.getOrCreateBalance(session.getUserId());
            String balanceMessage = MessageUtils.createBalanceInfoMessage(balance, session.getDisplayName());

            return MessageUtils.createEditMessageWithKeyboard(chatId, messageId, balanceMessage,
                    MessageUtils.createBalanceMenuKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при показе баланса для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createEditMessage(chatId, messageId,
                    "❌ Ошибка при загрузке баланса. Попробуйте позже.");
        }
    }
}
