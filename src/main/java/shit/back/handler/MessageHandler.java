package shit.back.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import shit.back.model.UserSession;
import shit.back.model.Order;
import shit.back.service.PriceService;
import shit.back.service.UserSessionService;
import shit.back.utils.MessageUtils;

import java.util.Optional;

@Component
public class MessageHandler {
    
    @Autowired
    private UserSessionService userSessionService;
    
    @Autowired
    private PriceService priceService;
    
    public SendMessage handleMessage(Message message) {
        String text = message.getText();
        User user = message.getFrom();
        Long chatId = message.getChatId();
        
        // Создаем или получаем сессию пользователя
        UserSession session = userSessionService.getOrCreateSession(
            user.getId(),
            user.getUserName(),
            user.getFirstName(),
            user.getLastName()
        );
        
        // Обработка команд
        if (text.startsWith("/")) {
            return handleCommand(text, chatId, session);
        }
        
        // Обработка обычных сообщений
        return handleTextMessage(text, chatId, session);
    }
    
    private SendMessage handleCommand(String command, Long chatId, UserSession session) {
        return switch (command.toLowerCase()) {
            case "/start" -> handleStartCommand(chatId, session);
            case "/help" -> handleHelpCommand(chatId);
            case "/prices" -> handlePricesCommand(chatId);
            case "/status" -> handleStatusCommand(chatId, session);
            case "/cancel" -> handleCancelCommand(chatId, session);
            default -> MessageUtils.createMessage(chatId, 
                "❓ Неизвестная команда. Используйте /help для просмотра доступных команд.");
        };
    }
    
    private SendMessage handleStartCommand(Long chatId, UserSession session) {
        String welcomeText = MessageUtils.formatWelcomeMessage(session.getDisplayName());
        return MessageUtils.createMessageWithKeyboard(chatId, welcomeText, 
            MessageUtils.createMainMenuKeyboard());
    }
    
    private SendMessage handleHelpCommand(Long chatId) {
        return MessageUtils.createMessage(chatId, MessageUtils.formatHelpMessage());
    }
    
    private SendMessage handlePricesCommand(Long chatId) {
        String pricesText = MessageUtils.formatPricesMessage(priceService.getAllPackages());
        return MessageUtils.createMessageWithKeyboard(chatId, pricesText,
            MessageUtils.createPackageSelectionKeyboard(priceService.getAllPackages()));
    }
    
    private SendMessage handleStatusCommand(Long chatId, UserSession session) {
        Optional<Order> activeOrder = userSessionService.getUserActiveOrder(session.getUserId());
        
        if (activeOrder.isPresent()) {
            Order order = activeOrder.get();
            String statusText = String.format("""
                📋 <b>Статус заказа %s</b>
                
                %s <b>Статус:</b> %s
                ⭐ <b>Звезды:</b> %d
                💰 <b>Сумма:</b> $%.2f
                📅 <b>Создан:</b> %s
                """,
                order.getFormattedOrderId(),
                order.getStatusEmoji(),
                order.getStatus().name(),
                order.getStarPackage().getStars(),
                order.getAmount(),
                order.getCreatedAt().toString()
            );
            
            return MessageUtils.createMessage(chatId, statusText);
        } else {
            return MessageUtils.createMessage(chatId, 
                "📋 У вас нет активных заказов.\n\n💰 Нажмите /start чтобы сделать новый заказ!");
        }
    }
    
    private SendMessage handleCancelCommand(Long chatId, UserSession session) {
        userSessionService.clearUserSession(session.getUserId());
        return MessageUtils.createMessage(chatId, 
            "❌ Текущая операция отменена.\n\n🏠 Возвращайтесь в главное меню: /start");
    }
    
    private SendMessage handleTextMessage(String text, Long chatId, UserSession session) {
        // Обработка обычных текстовых сообщений
        return switch (session.getState()) {
            case IDLE -> MessageUtils.createMessage(chatId, 
                "👋 Привет! Используйте /start для начала работы или /help для получения помощи.");
            
            case SELECTING_PACKAGE -> MessageUtils.createMessage(chatId,
                "📦 Пожалуйста, выберите пакет из предложенных вариантов, используя кнопки меню.");
            
            case CONFIRMING_ORDER -> MessageUtils.createMessage(chatId,
                "⏳ Пожалуйста, подтвердите заказ, используя кнопки меню.");
            
            case AWAITING_PAYMENT -> MessageUtils.createMessage(chatId,
                "💳 Ожидаем оплату. Выберите способ оплаты из предложенных вариантов.");
            
            case PAYMENT_PROCESSING -> MessageUtils.createMessage(chatId,
                "⚙️ Ваш платеж обрабатывается. Пожалуйста, подождите...");
            
            case COMPLETED -> MessageUtils.createMessage(chatId,
                "✅ Ваш заказ выполнен! Используйте /start для нового заказа.");
            
            default -> MessageUtils.createMessage(chatId,
                "🤔 Не понимаю. Используйте /help для получения помощи.");
        };
    }
}
