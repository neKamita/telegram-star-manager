package shit.back.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import shit.back.model.UserSession;
import shit.back.model.Order;
import shit.back.model.StarPackage;
import shit.back.security.RateLimitService;
import shit.back.security.SecurityValidator;
import shit.back.service.PriceService;
import shit.back.service.UserSessionService;
import shit.back.utils.MessageUtils;

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
            log.warn("❌ Невалидные callback данные от пользователя {}: {}", user.getId(), validationResult.getErrorMessage());
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
            log.warn("⚠️ Невалидное имя пользователя от user {}: {}", user.getId(), usernameValidation.getErrorMessage());
            // Продолжаем работу, но логируем предупреждение
        }
        
        // Получаем сессию пользователя
        UserSession session = userSessionService.getOrCreateSession(
            user.getId(),
            securityValidator.sanitizeText(user.getUserName()),
            securityValidator.sanitizeText(user.getFirstName()),
            securityValidator.sanitizeText(user.getLastName())
        );
        
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
                order.getCreatedAt().toLocalDate()
            );
            
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
    
    private EditMessageText handlePackageSelection(Long chatId, Integer messageId, UserSession session, String packageId) {
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
    
    private EditMessageText handleOrderConfirmation(Long chatId, Integer messageId, UserSession session, String orderId) {
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
    
    private EditMessageText handleCancelSpecificOrder(Long chatId, Integer messageId, UserSession session, String orderId) {
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
}
