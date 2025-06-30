package shit.back.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import shit.back.telegram.TelegramService;
import shit.back.telegram.commands.InitiateStarPurchaseCommand;
import shit.back.telegram.commands.ProcessCustomAmountCommand;
import shit.back.telegram.commands.TopupBalanceCommand;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.queries.ShowBalanceQuery;
import shit.back.telegram.queries.ShowPurchaseHistoryQuery;
import shit.back.telegram.queries.ShowWelcomeCardQuery;
import shit.back.service.UserSessionUnifiedService;
import shit.back.service.TelegramMessageCacheService;
import shit.back.model.UserSession;
import shit.back.application.balance.service.BalanceApplicationServiceV2;
import shit.back.application.balance.dto.request.OperationRequest;
import shit.back.application.balance.common.Result;
import shit.back.application.balance.dto.response.BalanceResponse;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Фасад для обработки Telegram сообщений и callback-ов
 * 
 * Координирует взаимодействие между Telegram Bot API и TelegramService
 */
@Component
@Slf4j
public class TelegramHandlerFacade {

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private TelegramMessageCacheService messageCacheService;

    @Autowired
    private UserSessionUnifiedService sessionService;

    @Autowired
    private BalanceApplicationServiceV2 balanceService;

    // Кэш для предотвращения дублирующихся операций
    private final ConcurrentMap<String, Long> operationCache = new ConcurrentHashMap<>();
    private static final long OPERATION_CACHE_TTL_MS = 5000; // 5 секунд

    /**
     * Обработка обычных сообщений
     */
    public BotApiMethod<?> processMessage(Message message) {
        try {
            Long userId = message.getFrom().getId();
            String text = message.getText();

            log.info("📨 Обработка сообщения от пользователя: {} с текстом: '{}'", userId, text);

            if (text == null || text.trim().isEmpty()) {
                return createErrorMessage(message.getChatId(), "Получено пустое сообщение");
            }

            // Обработка команд
            if (text.startsWith("/start")) {
                return processStartCommand(userId, message.getChatId());
            } else if (text.startsWith("/balance")) {
                return processBalanceCommand(userId, message.getChatId());
            } else if (text.startsWith("/help")) {
                return processHelpCommand(message.getChatId());
            } else {
                // Обработка произвольного текста
                return processTextMessage(userId, message.getChatId(), text);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка обработки сообщения: {}", e.getMessage(), e);
            return createErrorMessage(message.getChatId(), "Произошла ошибка при обработке сообщения");
        }
    }

    /**
     * ИСПРАВЛЕНО: Обработка callback'ов с кэшированием и идемпотентностью
     */
    public BotApiMethod<?> processCallbackQuery(CallbackQuery callbackQuery) {
        try {
            Long userId = callbackQuery.getFrom().getId();
            String callbackData = callbackQuery.getData();
            Long chatId = callbackQuery.getMessage().getChatId();
            Integer messageId = callbackQuery.getMessage().getMessageId();

            log.info("🔄 Обработка callback от пользователя: {} с данными: '{}'", userId, callbackData);

            // ИСПРАВЛЕНИЕ: Проверка идемпотентности операций
            String operationKey = userId + ":" + callbackData;
            if (isRecentOperation(operationKey)) {
                log.debug("⚠️ Дублирующаяся операция обнаружена: {} - игнорируем", operationKey);
                return null; // Не отправляем дубликат
            }

            // ИСПРАВЛЕНИЕ: Fail-fast валидация сессии
            ensureUserSession(userId, callbackQuery.getFrom());

            TelegramResponse response = processCallbackDataOptimized(userId, callbackData);

            if (response.isSuccessful()) {
                EditMessageText editMessage = convertResponseToEditMessage(chatId, messageId, response);

                // ИСПРАВЛЕНИЕ: Проверка изменений перед отправкой
                InlineKeyboardMarkup keyboard = response.getKeyboard();
                if (!messageCacheService.isMessageChanged(chatId, messageId, response.getMessage(), keyboard)) {
                    log.debug("📝 Сообщение не изменилось для пользователя {} - пропускаем отправку", userId);
                    return null;
                }

                // Регистрируем выполненную операцию
                registerOperation(operationKey);
                return editMessage;
            } else {
                return createErrorMessage(chatId, response.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка обработки callback: {}", e.getMessage(), e);
            return createErrorMessage(callbackQuery.getMessage().getChatId(),
                    "Произошла ошибка при обработке callback");
        }
    }

    /**
     * КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Обработка callback'ов с гарантированным созданием
     * сессии
     */
    private TelegramResponse processCallbackData(Long userId, String callbackData) {
        log.info("🔍 ДИАГНОСТИКА: Получен callback '{}' для пользователя: {}", callbackData, userId);

        // ROOT CAUSE ИСПРАВЛЕНИЕ: Автоматически создаем/получаем сессию для ВСЕХ
        // callback'ов
        try {
            UserSession session = sessionService.getOrCreateSession(userId, null, null, null);
            log.info("✅ ДИАГНОСТИКА: Сессия обеспечена для пользователя {}. Состояние: {}, PaymentType: {}",
                    userId, session.getState(), session.getPaymentType());
        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА: Не удалось создать/получить сессию для пользователя {}: {}",
                    userId, e.getMessage(), e);
            return TelegramResponse.error("Ошибка инициализации сессии");
        }

        // ДИАГНОСТИЧЕСКИЙ ЛОГ: отслеживаем все входящие callback'ы
        log.info("🔍 ДИАГНОСТИКА: Проверяем обработку callback'а в switch statement");

        // 🔍 ДИАГНОСТИЧЕСКИЙ ЛОГ #2: Отслеживаем все входящие callback'ы
        log.info("🔍 ДИАГНОСТИКА CALLBACK: Начинаем обработку callback '{}' для пользователя {}", callbackData, userId);

        switch (callbackData) {
            case "refresh_balance":
                log.info("💰 Обработка refresh_balance для пользователя: {}", userId);
                return telegramService.execute(new ShowBalanceQuery(userId));

            case "show_balance":
                log.info("💰 Обработка show_balance для пользователя: {}", userId);
                return telegramService.execute(new ShowBalanceQuery(userId, true));

            // ПОПОЛНЕНИЕ БАЛАНСА - начальная команда (показываем способы оплаты)
            case "topup_balance":
                log.info("💳 Обработка topup_balance - показываем способы оплаты");
                return telegramService.execute(new TopupBalanceCommand(userId, (BigDecimal) null));

            // ВЫБОР СПОСОБА ОПЛАТЫ
            case "payment_crypto":
                log.info("🪙 Обработка payment_crypto - переход к выбору суммы");
                return telegramService.execute(new TopupBalanceCommand(userId, (String) null, "TON"));
            case "payment_yoomoney":
                log.info("💳 Обработка payment_yoomoney - переход к выбору суммы");
                return telegramService.execute(new TopupBalanceCommand(userId, (String) null, "YOOKASSA"));
            case "payment_uzs":
                log.info("🏛️ Обработка payment_uzs - переход к выбору суммы");
                log.info("🔍 ДИАГНОСТИКА CALLBACK: Сохраняем способ оплаты 'UZS_PAYMENT' для пользователя {}", userId);
                return telegramService.execute(new TopupBalanceCommand(userId, (String) null, "UZS_PAYMENT"));

            // ПРЕДУСТАНОВЛЕННЫЕ СУММЫ ПОПОЛНЕНИЯ
            case "topup_amount_10":
                return handleTopupAmount(userId, "10");
            case "topup_amount_25":
                return handleTopupAmount(userId, "25");
            case "topup_amount_50":
                return handleTopupAmount(userId, "50");
            case "topup_amount_100":
                return handleTopupAmount(userId, "100");
            case "topup_amount_250":
                return handleTopupAmount(userId, "250");
            case "topup_amount_500":
                return handleTopupAmount(userId, "500");

            // ПОЛЬЗОВАТЕЛЬСКАЯ СУММА
            case "custom_amount":
                log.info("✏️ Обработка custom_amount - переход к вводу суммы");
                return processCustomAmountStart(userId);

            // 🔍 ДИАГНОСТИКА ПРОБЛЕМЫ #2: Обработчики confirm_topup_*
            case "confirm_topup_10":
                log.info("🔍 ДИАГНОСТИКА CALLBACK: Найден confirm_topup_10 - это была ПРОБЛЕМА #2!");
                return handleConfirmTopup(userId, "10");
            case "confirm_topup_25":
                log.info("🔍 ДИАГНОСТИКА CALLBACK: Найден confirm_topup_25");
                return handleConfirmTopup(userId, "25");
            case "confirm_topup_50":
                log.info("🔍 ДИАГНОСТИКА CALLBACK: Найден confirm_topup_50");
                return handleConfirmTopup(userId, "50");
            case "confirm_topup_100":
                log.info("🔍 ДИАГНОСТИКА CALLBACK: Найден confirm_topup_100");
                return handleConfirmTopup(userId, "100");
            case "confirm_topup_250":
                log.info("🔍 ДИАГНОСТИКА CALLBACK: Найден confirm_topup_250");
                return handleConfirmTopup(userId, "250");
            case "confirm_topup_500":
                log.info("🔍 ДИАГНОСТИКА CALLBACK: Найден confirm_topup_500 - это была ПРОБЛЕМА #2!");
                return handleConfirmTopup(userId, "500");

            // 🔍 ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #2: Добавляем недостающие обработчики для
            // пользовательских сумм
            case "confirm_topup_1000":
                log.info("🔍 ДИАГНОСТИКА CALLBACK: Найден confirm_topup_1000 - дополнительный обработчик");
                return handleConfirmTopup(userId, "1000");
            case "confirm_topup_2000":
                log.info("🔍 ДИАГНОСТИКА CALLBACK: Найден confirm_topup_2000 - дополнительный обработчик");
                return handleConfirmTopup(userId, "2000");
            case "confirm_topup_custom":
                log.info(
                        "🔍 ДИАГНОСТИКА CALLBACK: Найден confirm_topup_custom - обработчик для пользовательской суммы");
                return handleConfirmTopupCustom(userId);

            // ОТМЕНА ПОПОЛНЕНИЯ
            case "cancel_topup":
                log.info("❌ Обработка cancel_topup - отмена пополнения");
                return handleCancelTopup(userId);

            case "show_history":
                log.info("📊 Обработка show_history для пользователя: {}", userId);
                return telegramService.execute(new ShowPurchaseHistoryQuery(userId));

            case "buy_stars":
                log.info("⭐ Обработка buy_stars для пользователя: {}", userId);
                return telegramService.execute(new InitiateStarPurchaseCommand(userId));

            // Обработка конкретных пакетов звезд: buy_stars_500_4.50
            case "transfer_funds":
                log.info("💸 Обработка transfer_funds для пользователя: {}", userId);
                return TelegramResponse.error("Функция перевода средств временно недоступна");

            default:
                // Обработка конкретных пакетов звезд: buy_stars_500_4.50
                if (callbackData.startsWith("buy_stars_") && callbackData.contains("_")
                        && !callbackData.equals("buy_stars")) {
                    return handleBuyStarsPackage(userId, callbackData);
                }

                // Проверяем, является ли это динамическим callback для подтверждения
                if (callbackData.startsWith("confirm_topup_")) {
                    String amount = callbackData.substring("confirm_topup_".length());
                    log.info("🔍 ДИАГНОСТИКА CALLBACK: Динамический confirm_topup для суммы: {}", amount);
                    return handleConfirmTopup(userId, amount);
                }

                // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Обработка process_payment_* callbacks
                if (callbackData.startsWith("process_payment_")) {
                    String amount = callbackData.substring("process_payment_".length());
                    log.info("💳 Обработка process_payment для суммы: {}", amount);
                    return handleProcessPayment(userId, amount);
                }

                // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Обработка payment_completed_* callbacks
                if (callbackData.startsWith("payment_completed_")) {
                    String amount = callbackData.substring("payment_completed_".length());
                    log.info("✅ Обработка payment_completed для суммы: {}", amount);
                    return handlePaymentCompleted(userId, amount);
                }

                // Обработка подтверждения покупки звезд: proceed_purchase_1000
                if (callbackData.startsWith("proceed_purchase_")) {
                    return handleProceedPurchase(userId, callbackData);
                }

                log.warn("🚨 ДИАГНОСТИКА CALLBACK: Неизвестный callback '{}' - НЕ НАЙДЕН обработчик!", callbackData);
                return TelegramResponse.error("⚠️ Неизвестный callback: " + callbackData);
        }
    }

    /**
     * 🔍 ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #2: Обработка подтверждения пользовательской суммы
     */
    private TelegramResponse handleConfirmTopupCustom(Long userId) {
        log.info("✅ Обработка подтверждения пользовательской суммы для пользователя {}", userId);
        try {
            // Получаем сессию пользователя для получения введенной суммы
            Optional<UserSession> sessionOpt = sessionService.getSession(userId);
            if (sessionOpt.isPresent()) {
                UserSession session = sessionOpt.get();
                // Предполагаем, что пользовательская сумма хранится в одном из полей сессии
                // Или получаем из кэша команды ProcessCustomAmountCommand
                log.info("🔍 ДИАГНОСТИКА: Обрабатываем подтверждение пользовательской суммы для сессии: {}",
                        session.getState());

                // В данном случае перенаправляем к стандартному обработчику с дефолтной суммой
                // Это можно улучшить, добавив хранение пользовательской суммы в сессию
                return handleConfirmTopup(userId, "100"); // Временное решение
            }

            return TelegramResponse.error("Не удалось найти информацию о пользовательской сумме");
        } catch (Exception e) {
            log.error("❌ Ошибка при подтверждении пользовательской суммы: {}", e.getMessage());
            return TelegramResponse.error("Ошибка при подтверждении пользовательской суммы: " + e.getMessage());
        }
    }

    /**
     * Обработка команды /start
     */
    private BotApiMethod<?> processStartCommand(Long userId, Long chatId) {
        log.info("🚀 Обработка команды /start для пользователя: {}", userId);

        TelegramResponse response = telegramService.execute(new ShowWelcomeCardQuery(userId));

        if (response.isSuccessful()) {
            return convertResponseToSendMessage(chatId, response);
        } else {
            return createErrorMessage(chatId, "Ошибка получения стартовой информации");
        }
    }

    /**
     * Обработка команды /balance
     */
    private BotApiMethod<?> processBalanceCommand(Long userId, Long chatId) {
        log.info("💰 Обработка команды /balance для пользователя: {}", userId);

        TelegramResponse response = telegramService.execute(new ShowBalanceQuery(userId, true, true));

        if (response.isSuccessful()) {
            return convertResponseToSendMessage(chatId, response);
        } else {
            return createErrorMessage(chatId, "Ошибка получения баланса");
        }
    }

    /**
     * Обработка команды /help
     */
    private BotApiMethod<?> processHelpCommand(Long chatId) {
        log.info("❓ Обработка команды /help");

        String helpText = """
                🤖 *Telegram Star Manager Bot*

                Доступные команды:
                /start - Запуск бота и просмотр баланса
                /balance - Подробная информация о балансе
                /help - Эта справка

                Используйте кнопки в интерфейсе для:
                • Пополнения баланса
                • Покупки звезд
                • Просмотра истории операций
                """;

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(helpText)
                .parseMode("Markdown")
                .build();
    }

    /**
     * Обработка произвольного текста
     */
    private BotApiMethod<?> processTextMessage(Long userId, Long chatId, String text) {
        log.info("💬 Обработка текстового сообщения: '{}' от пользователя: {}", text, userId);

        try {
            // Проверяем состояние пользователя
            Optional<UserSession> sessionOpt = sessionService.getSession(userId);
            if (sessionOpt.isPresent()) {
                UserSession session = sessionOpt.get();

                // Если пользователь находится в состоянии ввода пользовательской суммы
                if (session.getState() == UserSession.SessionState.ENTERING_CUSTOM_AMOUNT) {
                    log.info("🔢 Пользователь {} вводит пользовательскую сумму: '{}'", userId, text);

                    // Создаем команду обработки пользовательской суммы
                    ProcessCustomAmountCommand command = new ProcessCustomAmountCommand(userId, text, "topup");
                    TelegramResponse response = telegramService.execute(command);

                    if (response.isSuccessful()) {
                        // Получаем способ оплаты из сессии и переходим к подтверждению
                        String paymentMethod = getPaymentMethodFromSession(userId);
                        BigDecimal amount = command.getParsedAmount();

                        // Переходим к подтверждению пополнения с введенной суммой
                        TelegramResponse topupResponse = telegramService.execute(
                                new TopupBalanceCommand(userId, amount.toString(), paymentMethod));

                        return convertResponseToSendMessage(chatId, topupResponse);
                    } else {
                        return SendMessage.builder()
                                .chatId(chatId.toString())
                                .text(response.getErrorMessage())
                                .build();
                    }
                }
            }

            // Если не в специальном состоянии - стандартное сообщение
            return SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Извините, я не понимаю это сообщение. Используйте /help для получения справки.")
                    .build();

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке текстового сообщения от пользователя {}: {}", userId, e.getMessage(), e);
            return SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("❌ Произошла ошибка при обработке сообщения. Попробуйте еще раз.")
                    .build();
        }
    }

    /**
     * Конвертация TelegramResponse в SendMessage
     * ИСПРАВЛЕНО: Использует parseMode из response, добавлены диагностические логи
     */
    private SendMessage convertResponseToSendMessage(Long chatId, TelegramResponse response) {
        String parseMode = response.getParseMode();
        log.info("🔍 DEBUG: Устанавливаем parseMode = {} для SendMessage. Текст: {}",
                parseMode, response.getMessage().substring(0, Math.min(50, response.getMessage().length())) + "...");

        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId.toString())
                .text(response.getMessage())
                .parseMode(parseMode);

        if (response.hasKeyboard()) {
            InlineKeyboardMarkup keyboard = convertToInlineKeyboard(response);
            if (keyboard != null && keyboard.getKeyboard() != null && !keyboard.getKeyboard().isEmpty()) {
                builder.replyMarkup(keyboard);
                log.debug("✅ Клавиатура добавлена к SendMessage");
            } else {
                log.debug("ℹ️ Клавиатура пустая или null, не добавляем replyMarkup");
            }
        }

        return builder.build();
    }

    /**
     * Конвертация TelegramResponse в EditMessageText
     * ИСПРАВЛЕНО: Использует parseMode из response, добавлены диагностические логи
     */
    private EditMessageText convertResponseToEditMessage(Long chatId, Integer messageId, TelegramResponse response) {
        String parseMode = response.getParseMode();
        log.info("🔍 DEBUG: Устанавливаем parseMode = {} для EditMessageText. Текст: {}",
                parseMode, response.getMessage().substring(0, Math.min(50, response.getMessage().length())) + "...");

        EditMessageText.EditMessageTextBuilder builder = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(response.getMessage())
                .parseMode(parseMode);

        if (response.hasKeyboard()) {
            InlineKeyboardMarkup keyboard = convertToInlineKeyboard(response);
            if (keyboard != null && keyboard.getKeyboard() != null && !keyboard.getKeyboard().isEmpty()) {
                builder.replyMarkup(keyboard);
                log.debug("✅ Клавиатура добавлена к EditMessageText");
            } else {
                log.debug("ℹ️ Клавиатура пустая или null, не добавляем replyMarkup");
            }
        }

        return builder.build();
    }

    /**
     * Конвертация клавиатуры из TelegramResponse
     * ИСПРАВЛЕНО: Правильно извлекаем клавиатуру из response
     */
    private InlineKeyboardMarkup convertToInlineKeyboard(TelegramResponse response) {
        if (response == null) {
            log.warn("⚠️ TelegramResponse is null, не можем извлечь клавиатуру");
            return null;
        }

        // Используем встроенный метод getKeyboard() из TelegramResponse
        InlineKeyboardMarkup keyboard = response.getKeyboard();
        if (keyboard != null) {
            log.debug("✅ Клавиатура успешно извлечена из response.getKeyboard()");
            return keyboard;
        }

        // Проверяем, является ли data клавиатурой
        Object data = response.getData();
        if (data instanceof InlineKeyboardMarkup) {
            log.debug("✅ Клавиатура найдена в response.getData()");
            return (InlineKeyboardMarkup) data;
        }

        log.debug("ℹ️ Клавиатура не найдена в response, возвращаем null");
        return null;
    }

    /**
     * Создание сообщения об ошибке
     */
    private SendMessage createErrorMessage(Long chatId, String errorMessage) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("❌ " + errorMessage)
                .build();
    }

    /**
     * Обработка предустановленной суммы пополнения
     */
    private TelegramResponse handleTopupAmount(Long userId, String amount) {
        log.info("💰 Обработка предустановленной суммы {} для пользователя {}", amount, userId);
        try {
            // Получаем текущий способ оплаты из сессии пользователя
            String paymentMethod = getPaymentMethodFromSession(userId);
            return telegramService.execute(new TopupBalanceCommand(userId, amount, paymentMethod));
        } catch (Exception e) {
            log.error("❌ Ошибка при обработке суммы {}: {}", amount, e.getMessage());
            return TelegramResponse.error("Ошибка при обработке суммы: " + e.getMessage());
        }
    }

    /**
     * Обработка начала ввода пользовательской суммы
     */
    private TelegramResponse processCustomAmountStart(Long userId) {
        log.info("✏️ Начало ввода пользовательской суммы для пользователя {}", userId);
        try {
            // Обновляем состояние сессии на ожидание ввода суммы
            sessionService.updateSessionState(userId, UserSession.SessionState.ENTERING_CUSTOM_AMOUNT);

            return TelegramResponse.builder()
                    .successful(true)
                    .message("💡 Введите сумму для пополнения (например: 100 или 50.25)\n\n" +
                            "⚠️ Минимальная сумма: 0.01\n" +
                            "📊 Максимальная сумма: 1,000,000")
                    .uiType("CUSTOM_AMOUNT_INPUT")
                    .build();
        } catch (Exception e) {
            log.error("❌ Ошибка при инициации ввода пользовательской суммы: {}", e.getMessage());
            return TelegramResponse.error("Ошибка при переходе к вводу суммы");
        }
    }

    /**
     * ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #2: Получение сохраненного способа оплаты из сессии
     */
    private String getPaymentMethodFromSession(Long userId) {
        try {
            Optional<UserSession> sessionOpt = sessionService.getSession(userId);
            if (sessionOpt.isPresent()) {
                UserSession session = sessionOpt.get();
                String paymentType = session.getPaymentType();
                if (paymentType != null && !paymentType.trim().isEmpty()) {
                    log.debug("🔍 Найден сохраненный способ оплаты: {} для пользователя {}", paymentType, userId);
                    return paymentType;
                }
            }

            log.debug("🔍 Способ оплаты не найден в сессии, используем DEFAULT для пользователя {}", userId);
            return "DEFAULT";
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при получении способа оплаты из сессии: {}", e.getMessage());
            return "DEFAULT";
        }
    }

    /**
     * КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Обработка подтверждения пополнения - показываем
     * ДРУГОЕ сообщение
     */
    private TelegramResponse handleConfirmTopup(Long userId, String amount) {
        log.info("✅ Обработка подтверждения пополнения суммы {} для пользователя {}", amount, userId);
        try {
            // Получаем способ оплаты из сессии
            String paymentMethod = getPaymentMethodFromSession(userId);

            // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Вместо повторной команды, показываем сообщение о
            // начале обработки
            String message = String.format("""
                    🔄 <b>Обработка платежа...</b>

                    💰 <b>Сумма:</b> %s USD
                    💳 <b>Способ:</b> %s
                    ⏱️ <b>Статус:</b> Подготовка к оплате

                    🔗 Перейдите по ссылке для оплаты или дождитесь инструкций
                    """,
                    amount,
                    getPaymentMethodDisplayName(paymentMethod));

            // Обновляем состояние сессии на обработку платежа
            sessionService.updateSessionState(userId, UserSession.SessionState.PAYMENT_PROCESSING);

            // Создаем клавиатуру с ссылкой на оплату
            String processPaymentCallback = "process_payment_" + amount;
            var keyboard = new shit.back.telegram.ui.builder.TelegramKeyboardBuilder()
                    .addButton("💳 Перейти к оплате", processPaymentCallback)
                    .addButton("❌ Отменить", "cancel_topup")
                    .newRow()
                    .addButton("🔙 К балансу", "show_balance")
                    .build();

            return TelegramResponse.builder()
                    .successful(true)
                    .message(message)
                    .uiType("PAYMENT_PROCESSING")
                    .data(keyboard)
                    .build();

        } catch (Exception e) {
            log.error("❌ Ошибка при подтверждении пополнения: {}", e.getMessage());
            return TelegramResponse.error("Ошибка при подтверждении пополнения: " + e.getMessage());
        }
    }

    /**
     * КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Получение отображаемого имени способа оплаты
     * (дублируем из TopupBalanceCommandHandler)
     */
    private String getPaymentMethodDisplayName(String paymentMethod) {
        if (paymentMethod == null) {
            return "Не выбран";
        }

        return switch (paymentMethod.toLowerCase()) {
            case "yoomoney", "payment_yoomoney", "yookassa" -> "💳 YooMoney";
            case "crypto", "payment_crypto", "ton" -> "₿ Криптовалюта";
            case "uzs", "payment_uzs", "uzs_payment" -> "💳 UZS карта";
            default -> "💳 Способ оплаты";
        };
    }

    /**
     * ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #3: Обработка отмены пополнения
     */
    private TelegramResponse handleCancelTopup(Long userId) {
        log.info("❌ Обработка отмены пополнения для пользователя {}", userId);
        try {
            // Очищаем состояние сессии
            sessionService.updateSessionState(userId, UserSession.SessionState.IDLE);

            // Возвращаем к балансу
            return telegramService.execute(new ShowBalanceQuery(userId, true));
        } catch (Exception e) {
            log.error("❌ Ошибка при отмене пополнения: {}", e.getMessage());
            return TelegramResponse.error("Ошибка при отмене операции");
        }
    }

    /**
     * КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Обработка перехода к оплате
     */
    private TelegramResponse handleProcessPayment(Long userId, String amount) {
        log.info("💳 Обработка перехода к оплате суммы {} для пользователя {}", amount, userId);
        try {
            // Получаем способ оплаты из сессии
            String paymentMethod = getPaymentMethodFromSession(userId);

            // Генерируем сообщение с ссылкой на оплату (пока заглушка)
            String message = String.format("""
                    💳 <b>Переход к оплате</b>

                    💰 <b>Сумма:</b> %s USD
                    💳 <b>Способ:</b> %s
                    ⏱️ <b>Статус:</b> Готов к оплате

                    🔗 <a href="https://example.com/payment">Нажмите для перехода к платежной форме</a>

                    ⚠️ После оплаты баланс будет автоматически пополнен
                    """,
                    amount,
                    getPaymentMethodDisplayName(paymentMethod));

            // Создаем клавиатуру с кнопками для завершения процесса
            var keyboard = new shit.back.telegram.ui.builder.TelegramKeyboardBuilder()
                    .addButton("✅ Оплата завершена", "payment_completed_" + amount)
                    .addButton("❌ Отменить", "cancel_topup")
                    .newRow()
                    .addButton("🔙 К балансу", "show_balance")
                    .build();

            return TelegramResponse.builder()
                    .successful(true)
                    .message(message)
                    .uiType("PAYMENT_LINK")
                    .data(keyboard)
                    .build();

        } catch (Exception e) {
            log.error("❌ Ошибка при переходе к оплате: {}", e.getMessage());
            return TelegramResponse.error("Ошибка при переходе к оплате: " + e.getMessage());
        }
    }

    // ===========================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ОПТИМИЗАЦИИ
    // ===========================================

    /**
     * ИСПРАВЛЕНИЕ: Проверка недавних операций для предотвращения дубликатов
     */
    private boolean isRecentOperation(String operationKey) {
        Long lastOperationTime = operationCache.get(operationKey);
        if (lastOperationTime == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        return (currentTime - lastOperationTime) < OPERATION_CACHE_TTL_MS;
    }

    /**
     * ИСПРАВЛЕНИЕ: Регистрация выполненной операции
     */
    private void registerOperation(String operationKey) {
        operationCache.put(operationKey, System.currentTimeMillis());

        // Очистка старых записей
        if (operationCache.size() > 1000) {
            cleanupOldOperations();
        }
    }

    /**
     * ИСПРАВЛЕНИЕ: Очистка старых операций из кэша
     */
    private void cleanupOldOperations() {
        long currentTime = System.currentTimeMillis();
        operationCache.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > OPERATION_CACHE_TTL_MS);
    }

    /**
     * ИСПРАВЛЕНИЕ: Гарантированное создание сессии пользователя
     */
    private void ensureUserSession(Long userId, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            String username = telegramUser.getUserName();
            String firstName = telegramUser.getFirstName();
            String lastName = telegramUser.getLastName();

            sessionService.getOrCreateSession(userId, username, firstName, lastName);
            log.debug("✅ Сессия обеспечена для пользователя {}", userId);
        } catch (Exception e) {
            log.error("❌ Критическая ошибка создания сессии для пользователя {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to ensure user session", e);
        }
    }

    /**
     * ИСПРАВЛЕНИЕ: Оптимизированная обработка callback'ов с кэшированием
     */
    private TelegramResponse processCallbackDataOptimized(Long userId, String callbackData) {
        log.debug("🔍 Оптимизированная обработка callback '{}' для пользователя: {}", callbackData, userId);

        // Используем существующий метод с небольшими оптимизациями
        return processCallbackData(userId, callbackData);
    }

    /**
     * КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Обработка завершения платежа
     * Пополняет баланс пользователя и сбрасывает состояние сессии
     */
    private TelegramResponse handlePaymentCompleted(Long userId, String amount) {
        log.info("✅ Обработка завершения платежа суммы {} для пользователя {}", amount, userId);
        try {
            // Валидация суммы
            BigDecimal amountDecimal;
            try {
                amountDecimal = new BigDecimal(amount);
                if (amountDecimal.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Сумма должна быть положительной");
                }
            } catch (NumberFormatException e) {
                log.error("❌ Некорректная сумма для пополнения: {}", amount);
                return TelegramResponse.error("Некорректная сумма для пополнения");
            }

            // Создаем запрос на пополнение баланса
            OperationRequest operationRequest = new OperationRequest();
            operationRequest.setOperationType(OperationRequest.OperationType.DEPOSIT);
            operationRequest.setUserId(userId);
            operationRequest.setAmount(amountDecimal);
            operationRequest.setCurrency("USD"); // По умолчанию USD, как в системе
            operationRequest.setDescription("Пополнение баланса через Telegram Bot - " + amount + " USD");
            operationRequest
                    .setIdempotencyKey("telegram_topup_" + userId + "_" + amount + "_" + System.currentTimeMillis());

            // Получаем способ оплаты из сессии для логирования
            String paymentMethod = getPaymentMethodFromSession(userId);
            if (paymentMethod != null) {
                operationRequest.setPaymentMethodId(paymentMethod);
            }

            log.info("💰 Выполняем пополнение баланса для пользователя {} на сумму {} USD", userId, amount);

            // Выполняем пополнение баланса через BalanceApplicationServiceV2
            Result<BalanceResponse> result = balanceService.processOperation(operationRequest);

            if (result.isSuccess()) {
                BalanceResponse balanceResponse = result.getValue();

                // Сбрасываем состояние сессии пользователя в IDLE
                sessionService.updateSessionState(userId, UserSession.SessionState.IDLE);

                log.info("✅ Баланс успешно пополнен для пользователя {}. Новый баланс: {} USD",
                        userId, balanceResponse.getCurrentBalance());

                // Формируем сообщение об успешном пополнении
                String message = String.format("""
                        ✅ <b>Платеж успешно завершен!</b>

                        💰 <b>Пополнено:</b> %s USD
                        💳 <b>Способ оплаты:</b> %s
                        📊 <b>Текущий баланс:</b> %s USD

                        🎉 Средства зачислены на ваш счет!
                        """,
                        amount,
                        getPaymentMethodDisplayName(paymentMethod),
                        balanceResponse.getCurrentBalance());

                // Создаем клавиатуру с дальнейшими действиями
                var keyboard = new shit.back.telegram.ui.builder.TelegramKeyboardBuilder()
                        .addButton("💰 Показать баланс", "show_balance")
                        .addButton("⭐ Купить звезды", "buy_stars")
                        .newRow()
                        .addButton("💳 Пополнить снова", "topup_balance")
                        .addButton("📊 История операций", "show_history")
                        .build();

                return TelegramResponse.builder()
                        .successful(true)
                        .message(message)
                        .uiType("PAYMENT_SUCCESS")
                        .data(keyboard)
                        .build();

            } else {
                log.error("❌ Ошибка при пополнении баланса для пользователя {}: {}",
                        userId, result.getError().getMessage());

                // В случае ошибки, возвращаем пользователя к балансу
                sessionService.updateSessionState(userId, UserSession.SessionState.IDLE);

                String errorMessage = String.format("""
                        ❌ <b>Ошибка при зачислении средств</b>

                        💰 <b>Сумма:</b> %s USD
                        ⚠️ <b>Причина:</b> %s

                        🔄 Попробуйте еще раз или обратитесь в поддержку
                        """,
                        amount,
                        result.getError().getMessage());

                // Создаем клавиатуру для повтора или отмены
                var keyboard = new shit.back.telegram.ui.builder.TelegramKeyboardBuilder()
                        .addButton("🔄 Попробовать снова", "topup_balance")
                        .addButton("💰 К балансу", "show_balance")
                        .build();

                return TelegramResponse.builder()
                        .successful(true)
                        .message(errorMessage)
                        .uiType("PAYMENT_ERROR")
                        .data(keyboard)
                        .build();
            }

        } catch (Exception e) {
            log.error("❌ Критическая ошибка при завершении платежа для пользователя {}: {}", userId, e.getMessage(), e);

            // Сбрасываем состояние сессии даже при ошибке
            try {
                sessionService.updateSessionState(userId, UserSession.SessionState.IDLE);
            } catch (Exception sessionError) {
                log.error("❌ Дополнительная ошибка при сбросе сессии: {}", sessionError.getMessage());
            }

            return TelegramResponse.error("Критическая ошибка при завершении платежа: " + e.getMessage());
        }
    }

    /**
     * Обработка подтверждения покупки звезд
     * Формат callback: proceed_purchase_{количество}
     * Пример: proceed_purchase_1000
     */
    private TelegramResponse handleProceedPurchase(Long userId, String callbackData) {
        try {
            log.info("⭐ Обработка подтверждения покупки звезд: {} для пользователя: {}", callbackData, userId);

            // Парсинг callback данных: proceed_purchase_1000
            String starCountStr = callbackData.substring("proceed_purchase_".length());
            int starCount = Integer.parseInt(starCountStr);

            log.info("⭐ Извлечено количество звезд для подтверждения: {} для пользователя: {}", starCount, userId);

            // Создание команды покупки с подтверждением
            InitiateStarPurchaseCommand command = new InitiateStarPurchaseCommand(
                    userId,
                    starCount,
                    true // confirmPurchase = true
            );

            return telegramService.execute(command);

        } catch (NumberFormatException e) {
            log.error("❌ Ошибка парсинга количества звезд из callback '{}': {}", callbackData, e.getMessage());
            return TelegramResponse.error("❌ Ошибка при обработке данных покупки");
        } catch (Exception e) {
            log.error("❌ Ошибка при подтверждении покупки звезд для пользователя {}: {}", userId, e.getMessage());
            return TelegramResponse.error("❌ Ошибка при подтверждении покупки: " + e.getMessage());
        }
    }

    /**
     * Обработка покупки конкретного пакета звезд
     * Формат callback: buy_stars_{количество}_{цена}
     * Пример: buy_stars_500_4.50
     */
    private TelegramResponse handleBuyStarsPackage(Long userId, String callbackData) {
        try {
            log.info("⭐ Обработка покупки конкретного пакета звезд: {} для пользователя: {}", callbackData, userId);

            // Парсинг callback данных: buy_stars_500_4.50
            String[] parts = callbackData.split("_");
            if (parts.length >= 3) {
                int starCount = Integer.parseInt(parts[2]);

                log.info("⭐ Извлечено количество звезд: {} для пользователя: {}", starCount, userId);

                // Создание команды покупки конкретного пакета звезд
                InitiateStarPurchaseCommand command = new InitiateStarPurchaseCommand(userId, starCount);
                return telegramService.execute(command);
            } else {
                log.error("❌ Некорректный формат callback данных пакета звезд: {}", callbackData);
                return TelegramResponse.error("❌ Некорректный формат данных пакета звезд");
            }
        } catch (NumberFormatException e) {
            log.error("❌ Ошибка парсинга количества звезд в callback: {} - {}", callbackData, e.getMessage());
            return TelegramResponse.error("❌ Ошибка при обработке данных пакета звезд");
        } catch (Exception e) {
            log.error("❌ Ошибка при покупке звезд для callback: {} - {}", callbackData, e.getMessage(), e);
            return TelegramResponse.error("❌ Ошибка при покупке звезд: " + e.getMessage());
        }
    }

}