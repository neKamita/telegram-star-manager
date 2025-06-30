package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис кэширования сообщений Telegram для предотвращения дубликатов
 * 
 * Решает проблему: "message is not modified: specified new message content and
 * reply markup are exactly the same"
 * 
 * Принципы:
 * - Single Responsibility: кэширование состояний сообщений
 * - Performance: быстрая проверка в памяти
 * - Memory Management: автоочистка старых записей
 */
@Service
@Slf4j
public class TelegramMessageCacheService {

    private final Map<String, CachedMessage> messageCache = new ConcurrentHashMap<>();
    private static final int CACHE_CLEANUP_HOURS = 1;

    /**
     * Проверить, отличается ли новое сообщение от кэшированного
     */
    public boolean isMessageChanged(Long chatId, Integer messageId, String newText, InlineKeyboardMarkup newKeyboard) {
        if (chatId == null || messageId == null || newText == null) {
            return true; // Считаем изменённым, если нет полных данных
        }

        String cacheKey = getCacheKey(chatId, messageId);
        CachedMessage cached = messageCache.get(cacheKey);

        // 🔍 ДИАГНОСТИЧЕСКИЙ ЛОГ #3: Проверяем работу кэширования сообщений
        log.info("🔍 ДИАГНОСТИКА КЭША: Проверяем изменения для chatId={}, messageId={}", chatId, messageId);
        log.info("🔍 ДИАГНОСТИКА КЭША: Новый текст: '{}'",
                newText.length() > 100 ? newText.substring(0, 100) + "..." : newText);

        if (cached == null) {
            // Первое сообщение - всегда считаем изменённым
            cacheMessage(cacheKey, newText, newKeyboard);
            log.info("🔍 ДИАГНОСТИКА КЭША: Первое сообщение для чата {} сообщение {} - разрешаем отправку", chatId,
                    messageId);
            return true;
        }

        boolean textChanged = !Objects.equals(cached.text, newText);
        boolean keyboardChanged = !areKeyboardsEqual(cached.keyboard, newKeyboard);

        // 🔍 ДИАГНОСТИЧЕСКИЙ ЛОГ #3: Детали сравнения
        log.info("🔍 ДИАГНОСТИКА КЭША: Кэшированный текст: '{}'",
                cached.text != null && cached.text.length() > 100 ? cached.text.substring(0, 100) + "..."
                        : cached.text);
        log.info("🔍 ДИАГНОСТИКА КЭША: Сравнение - текст изменился: {}, клавиатура изменилась: {}", textChanged,
                keyboardChanged);

        if (textChanged || keyboardChanged) {
            // Обновляем кэш с новыми данными
            cacheMessage(cacheKey, newText, newKeyboard);
            log.info("🔍 ДИАГНОСТИКА КЭША: Сообщение ИЗМЕНИЛОСЬ для чата {} сообщение {} - разрешаем отправку", chatId,
                    messageId);
            return true;
        }

        // 🔍 ДИАГНОСТИКА ПРОБЛЕМЫ #3: Дополнительная информация для отладки
        log.warn("🚨 ДИАГНОСТИКА КЭША: Сообщение НЕ изменилось для чата {} сообщение {} - БЛОКИРУЕМ отправку!", chatId,
                messageId);
        log.warn("🔍 ДИАГНОСТИКА КЭША: Детали блокировки:");
        log.warn("🔍 ДИАГНОСТИКА КЭША:   - Текст изменился: {}", textChanged);
        log.warn("🔍 ДИАГНОСТИКА КЭША:   - Клавиатура изменилась: {}", keyboardChanged);
        log.warn("🔍 ДИАГНОСТИКА КЭША:   - Время кэширования: {}", cached.timestamp);
        log.warn(
                "🔍 ДИАГНОСТИКА КЭША: Рекомендация - проверить логику генерации уникального контента или принудительно обновить UI");
        return false;
    }

    /**
     * Кэшировать сообщение
     */
    private void cacheMessage(String cacheKey, String text, InlineKeyboardMarkup keyboard) {
        messageCache.put(cacheKey, new CachedMessage(text, keyboard, LocalDateTime.now()));
    }

    /**
     * Генерация ключа кэша
     */
    private String getCacheKey(Long chatId, Integer messageId) {
        return chatId + ":" + messageId;
    }

    /**
     * Сравнение клавиатур
     */
    private boolean areKeyboardsEqual(InlineKeyboardMarkup keyboard1, InlineKeyboardMarkup keyboard2) {
        if (keyboard1 == null && keyboard2 == null) {
            return true;
        }
        if (keyboard1 == null || keyboard2 == null) {
            return false;
        }

        // Упрощённое сравнение - сравниваем количество строк и кнопок
        var rows1 = keyboard1.getKeyboard();
        var rows2 = keyboard2.getKeyboard();

        if (rows1 == null && rows2 == null) {
            return true;
        }
        if (rows1 == null || rows2 == null) {
            return false;
        }
        if (rows1.size() != rows2.size()) {
            return false;
        }

        for (int i = 0; i < rows1.size(); i++) {
            var row1 = rows1.get(i);
            var row2 = rows2.get(i);

            if (row1.size() != row2.size()) {
                return false;
            }

            for (int j = 0; j < row1.size(); j++) {
                var button1 = row1.get(j);
                var button2 = row2.get(j);

                if (!Objects.equals(button1.getText(), button2.getText()) ||
                        !Objects.equals(button1.getCallbackData(), button2.getCallbackData())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Очистка старых записей из кэша
     */
    public void cleanupOldEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(CACHE_CLEANUP_HOURS);
        int initialSize = messageCache.size();

        messageCache.entrySet().removeIf(entry -> entry.getValue().timestamp.isBefore(cutoff));

        int removed = initialSize - messageCache.size();
        if (removed > 0) {
            log.info("Очищено {} старых записей из кэша сообщений", removed);
        }
    }

    /**
     * ИСПРАВЛЕНИЕ: Автоматическая очистка кэша каждые 30 минут
     */
    @Scheduled(fixedRate = 1800000) // 30 минут
    public void scheduledCleanup() {
        try {
            cleanupOldEntries();
        } catch (Exception e) {
            log.error("Ошибка при автоматической очистке кэша сообщений: {}", e.getMessage(), e);
        }
    }

    /**
     * Получить размер кэша
     */
    public int getCacheSize() {
        return messageCache.size();
    }

    /**
     * Очистить весь кэш
     */
    public void clearCache() {
        messageCache.clear();
        log.info("Кэш сообщений полностью очищен");
    }

    /**
     * Внутренний класс для кэшированного сообщения
     */
    private static class CachedMessage {
        final String text;
        final InlineKeyboardMarkup keyboard;
        final LocalDateTime timestamp;

        CachedMessage(String text, InlineKeyboardMarkup keyboard, LocalDateTime timestamp) {
            this.text = text;
            this.keyboard = keyboard;
            this.timestamp = timestamp;
        }
    }
}