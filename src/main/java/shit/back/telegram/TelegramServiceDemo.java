package shit.back.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shit.back.telegram.commands.TopupBalanceCommand;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.queries.ShowBalanceQuery;
import shit.back.telegram.ui.TelegramUIResponse;

import java.math.BigDecimal;

/**
 * Демонстрация использования новой оптимизированной архитектуры Telegram
 * 
 * Показывает простоту использования единого API TelegramService
 */
@Component
@Slf4j
public class TelegramServiceDemo {

    @Autowired
    private TelegramService telegramService;

    /**
     * Пример 1: Выполнение запроса на баланс
     */
    public TelegramResponse getBalanceExample(Long userId) {
        log.info("📊 Демонстрация: получение баланса для пользователя {}", userId);

        // Создаем запрос
        ShowBalanceQuery query = new ShowBalanceQuery(userId);

        // Выполняем через единый сервис
        TelegramResponse response = telegramService.execute(query);

        log.info("✅ Результат запроса баланса: {}", response.isSuccessful() ? "успех" : "ошибка");
        return response;
    }

    /**
     * Пример 2: Выполнение команды пополнения баланса
     */
    public TelegramResponse topupBalanceExample(Long userId, BigDecimal amount) {
        log.info("💳 Демонстрация: пополнение баланса для пользователя {} на сумму {}", userId, amount);

        // Создаем команду
        TopupBalanceCommand command = new TopupBalanceCommand(userId, amount);

        // Выполняем через единый сервис
        TelegramResponse response = telegramService.execute(command);

        log.info("✅ Результат команды пополнения: {}", response.isSuccessful() ? "успех" : "ошибка");
        return response;
    }

    /**
     * Пример 3: Создание UI компонентов
     */
    public TelegramUIResponse createWelcomeUIExample(Long chatId, String userName) {
        log.info("🎨 Демонстрация: создание приветственного UI для пользователя {}", userName);

        // Используем UI Factory через TelegramService
        TelegramUIResponse uiResponse = telegramService.ui().createWelcomeMessage(chatId, userName);

        log.info("✅ UI компонент создан: {} символов, есть клавиатура: {}",
                uiResponse.getMessageText().length(), uiResponse.hasKeyboard());
        return uiResponse;
    }

    /**
     * Пример 4: Создание сообщения об ошибке
     */
    public TelegramUIResponse createErrorUIExample(Long chatId, String errorText) {
        log.info("❌ Демонстрация: создание UI ошибки для чата {}", chatId);

        // Используем UI Factory для создания сообщения об ошибке
        TelegramUIResponse uiResponse = telegramService.ui().createErrorMessage(chatId, errorText);

        log.info("✅ UI ошибки создан: {}", uiResponse.getMessageText().length());
        return uiResponse;
    }

    /**
     * Демонстрация полного цикла: команда + UI ответ
     */
    public TelegramUIResponse fullCycleExample(Long userId, Long chatId) {
        log.info("🔄 Демонстрация: полный цикл выполнения для пользователя {}", userId);

        try {
            // 1. Выполняем запрос баланса
            TelegramResponse balanceResponse = getBalanceExample(userId);

            if (balanceResponse.isSuccessful()) {
                // 2. Создаем UI с данными баланса
                return telegramService.ui().createBalanceMessage(chatId, balanceResponse.getData(), "Пользователь");
            } else {
                // 3. Создаем UI ошибки
                return telegramService.ui().createErrorMessage(chatId, balanceResponse.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка в демонстрации полного цикла: {}", e.getMessage(), e);
            return telegramService.ui().createErrorMessage(chatId, "Внутренняя ошибка системы");
        }
    }

    /**
     * Получить статистику работы TelegramService
     */
    public void printServiceStats() {
        log.info("📊 === СТАТИСТИКА TELEGRAM SERVICE ===");

        TelegramService.TelegramServiceStats stats = telegramService.getStats();
        log.info("📝 Обработчиков команд: {}", stats.commandHandlersCount());
        log.info("📊 Обработчиков запросов: {}", stats.queryHandlersCount());
        log.info("⚡ Выполнено команд: {}", stats.totalCommandsProcessed());
        log.info("🔍 Выполнено запросов: {}", stats.totalQueriesProcessed());
        log.info("❌ Всего ошибок: {}", stats.totalErrors());

        log.info("📋 Зарегистрированные команды: {}", stats.registeredCommands());
        log.info("📋 Зарегистрированные запросы: {}", stats.registeredQueries());

        TelegramService.TelegramServiceHealth health = telegramService.getHealth();
        log.info("💚 Статус здоровья: {}", health.status());
        log.info("📊 === КОНЕЦ СТАТИСТИКИ ===");
    }
}