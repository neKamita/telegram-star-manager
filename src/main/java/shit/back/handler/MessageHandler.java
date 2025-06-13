package shit.back.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.UserBalanceEntity;
import shit.back.exception.BalanceException;
import shit.back.exception.InsufficientBalanceException;
import shit.back.model.UserSession;
import shit.back.model.Order;
import shit.back.service.BalanceService;
import shit.back.service.BalanceTransactionService;
import shit.back.service.PriceService;
import shit.back.service.UserSessionService;
import shit.back.utils.MessageUtils;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class MessageHandler {

    @Autowired
    private UserSessionService userSessionService;

    @Autowired
    private PriceService priceService;

    // === ИНТЕГРАЦИЯ С СИСТЕМОЙ БАЛАНСА ===
    @Autowired
    private BalanceService balanceService;

    @Autowired
    private BalanceTransactionService balanceTransactionService;

    public SendMessage handleMessage(Message message) {
        String text = message.getText();
        User user = message.getFrom();
        Long chatId = message.getChatId();

        log.info("💬 MessageHandler: обработка сообщения от {} (ID: {}): {}",
                user.getFirstName(), user.getId(), text);

        // Создаем или получаем сессию пользователя
        UserSession session = userSessionService.getOrCreateSession(
                user.getId(),
                user.getUserName(),
                user.getFirstName(),
                user.getLastName());

        log.debug("👤 Сессия пользователя: состояние = {}", session.getState());

        SendMessage response;

        // Обработка команд
        if (text.startsWith("/")) {
            log.info("⚡ Обработка команды: {}", text);
            response = handleCommand(text, chatId, session);
        } else {
            log.info("📝 Обработка текстового сообщения в состоянии: {}", session.getState());
            response = handleTextMessage(text, chatId, session);
        }

        log.info("📨 MessageHandler: ответ подготовлен для chatId {}", chatId);
        return response;
    }

    private SendMessage handleCommand(String command, Long chatId, UserSession session) {
        return switch (command.toLowerCase()) {
            case "/start" -> handleStartCommand(chatId, session);
            case "/help" -> handleHelpCommand(chatId);
            case "/prices" -> handlePricesCommand(chatId);
            case "/status" -> handleStatusCommand(chatId, session);
            case "/cancel" -> handleCancelCommand(chatId, session);
            case "/beta" -> handleBetaCommand(chatId, session);
            case "/premium" -> handlePremiumCommand(chatId, session);
            // === НОВЫЕ КОМАНДЫ БАЛАНСА ===
            case "/balance" -> handleBalanceCommand(chatId, session);
            case "/topup" -> handleTopupCommand(chatId, session);
            case "/history" -> handleHistoryCommand(chatId, session);
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
                    order.getCreatedAt().toString());

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

    /**
     * Обработка бета-функций (Feature Flags удалены - всегда доступно)
     */
    private SendMessage handleBetaCommand(Long chatId, UserSession session) {
        String betaText = """
                🧪 <b>Бета-функции</b>

                ✨ Вы получили доступ к новым экспериментальным функциям!

                🔸 Улучшенная аналитика заказов
                🔸 Персонализированные рекомендации
                🔸 Быстрые платежи
                🔸 Расширенная статистика

                ⚠️ <i>Эти функции находятся в тестировании</i>
                """;

        return MessageUtils.createMessage(chatId, betaText);
    }

    /**
     * Обработка премиум-функций (Feature Flags удалены - всегда доступно)
     */
    private SendMessage handlePremiumCommand(Long chatId, UserSession session) {
        String premiumText = """
                💎 <b>Премиум функции</b>

                🌟 Добро пожаловать в VIP зону!

                ⚡ Мгновенная обработка заказов
                💰 Эксклюзивные скидки до 30%
                🎁 Бонусные звезды за каждую покупку
                📞 Приоритетная поддержка 24/7
                🏆 Доступ к лимитированным пакетам

                ✨ <i>Ваш статус: VIP пользователь</i>
                """;

        return MessageUtils.createMessage(chatId, premiumText);
    }

    private SendMessage handleTextMessage(String text, Long chatId, UserSession session) {
        // Обработка обычных текстовых сообщений
        return switch (session.getState()) {
            case IDLE -> MessageUtils.createMessage(chatId,
                    "👋 Привет! Используйте /start для начала работы или /help для получения помощи.");

            case SELECTING_PACKAGE -> MessageUtils.createMessage(chatId,
                    "� Пожалуйста, выберите пакет из предложенных вариантов, используя кнопки меню.");

            case CONFIRMING_ORDER -> MessageUtils.createMessage(chatId,
                    "⏳ Пожалуйста, подтвердите заказ, используя кнопки меню.");

            case AWAITING_PAYMENT -> MessageUtils.createMessage(chatId,
                    "� Ожидаем оплату. Выберите способ оплаты из предложенных вариантов.");

            case PAYMENT_PROCESSING -> MessageUtils.createMessage(chatId,
                    "⚙️ Ваш платеж обрабатывается. Пожалуйста, подождите...");

            case COMPLETED -> MessageUtils.createMessage(chatId,
                    "✅ Ваш заказ выполнен! Используйте /start для нового заказа.");

            default -> MessageUtils.createMessage(chatId,
                    "🤔 Не понимаю. Используйте /help для получения помощи.");
        };
    }

    // ============================================
    // === НОВЫЕ ОБРАБОТЧИКИ КОМАНД БАЛАНСА ===
    // ============================================

    /**
     * Обработка команды /balance - показать текущий баланс пользователя
     */
    private SendMessage handleBalanceCommand(Long chatId, UserSession session) {
        log.info("💰 Обработка команды /balance для пользователя: {}", session.getUserId());

        try {
            UserBalanceEntity balance = balanceService.getOrCreateBalance(session.getUserId());
            String balanceMessage = MessageUtils.createBalanceInfoMessage(balance, session.getDisplayName());

            return MessageUtils.createMessageWithKeyboard(chatId, balanceMessage,
                    MessageUtils.createBalanceMenuKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при получении баланса для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createMessage(chatId,
                    "❌ Ошибка при получении информации о балансе. Попробуйте позже.");
        }
    }

    /**
     * Обработка команды /topup - пополнить баланс
     */
    private SendMessage handleTopupCommand(Long chatId, UserSession session) {
        log.info("💳 Обработка команды /topup для пользователя: {}", session.getUserId());

        try {
            // Обновляем состояние сессии
            userSessionService.updateSessionState(session.getUserId(), UserSession.SessionState.TOPPING_UP_BALANCE);

            UserBalanceEntity balance = balanceService.getOrCreateBalance(session.getUserId());
            String topupMessage = MessageUtils.createTopupMessage(balance);

            return MessageUtils.createMessageWithKeyboard(chatId, topupMessage,
                    MessageUtils.createTopupKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при инициализации пополнения для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createMessage(chatId,
                    "❌ Ошибка при инициализации пополнения баланса. Попробуйте позже.");
        }
    }

    /**
     * Обработка команды /history - показать историю транзакций
     */
    private SendMessage handleHistoryCommand(Long chatId, UserSession session) {
        log.info("📊 Обработка команды /history для пользователя: {}", session.getUserId());

        try {
            // Получаем последние 10 транзакций
            List<BalanceTransactionEntity> history = balanceService.getBalanceHistory(session.getUserId(), 10);
            String historyMessage = MessageUtils.createBalanceHistoryMessage(history, session.getDisplayName());

            return MessageUtils.createMessageWithKeyboard(chatId, historyMessage,
                    MessageUtils.createBalanceHistoryKeyboard());

        } catch (Exception e) {
            log.error("❌ Ошибка при получении истории для пользователя {}: {}",
                    session.getUserId(), e.getMessage(), e);
            return MessageUtils.createMessage(chatId,
                    "❌ Ошибка при получении истории транзакций. Попробуйте позже.");
        }
    }
}
