package shit.back.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.telegram.commands.TelegramCommand;
import shit.back.telegram.commands.TelegramCommandHandler;
import shit.back.telegram.dto.TelegramRequest;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.queries.TelegramQuery;
import shit.back.telegram.queries.TelegramQueryHandler;
import shit.back.telegram.ui.TelegramUIFactory;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единый координатор всех Telegram операций
 * 
 * Объединяет функциональность старых TelegramApplicationService,
 * TelegramPresentationService и части TelegramInfrastructureService
 * в простой и удобный API
 */
@Service
@Transactional
@Slf4j
public class TelegramService {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TelegramUIFactory uiFactory;

    // Реестры обработчиков
    private final Map<Class<? extends TelegramCommand>, TelegramCommandHandler<?>> commandHandlers = new ConcurrentHashMap<>();
    private final Map<Class<? extends TelegramQuery>, TelegramQueryHandler<?>> queryHandlers = new ConcurrentHashMap<>();

    // Статистика
    private long totalCommandsProcessed = 0;
    private long totalQueriesProcessed = 0;
    private long totalErrors = 0;

    /**
     * Автоматическая регистрация всех обработчиков при инициализации
     */
    @PostConstruct
    public void initializeHandlers() {
        log.info("🚀 Инициализация TelegramService - единого координатора Telegram операций");

        registerCommandHandlers();
        registerQueryHandlers();

        log.info("✅ TelegramService инициализирован: {} CommandHandlers, {} QueryHandlers",
                commandHandlers.size(), queryHandlers.size());
    }

    /**
     * Выполнение команды (CQRS Write Side)
     */
    @SuppressWarnings("unchecked")
    public TelegramResponse executeCommand(TelegramCommand command) {
        log.info("⚡ Выполнение команды: {} для пользователя: {}",
                command.getCommandType(), command.getUserId());

        try {
            // Валидация команды
            command.validate();

            // Поиск обработчика
            TelegramCommandHandler<TelegramCommand> handler = (TelegramCommandHandler<TelegramCommand>) commandHandlers
                    .get(command.getClass());

            if (handler == null || !handler.isEnabled()) {
                String error = "Обработчик команды не найден или отключен: " + command.getCommandType();
                log.error("❌ {}", error);
                totalErrors++;
                return TelegramResponse.error(error);
            }

            // Выполнение команды
            TelegramResponse response = handler.handle(command);

            if (response.isSuccessful()) {
                log.info("✅ Команда успешно выполнена: {} для пользователя: {}",
                        command.getCommandType(), command.getUserId());
                totalCommandsProcessed++;
            } else {
                log.warn("⚠️ Команда выполнена с ошибкой: {} для пользователя: {} - {}",
                        command.getCommandType(), command.getUserId(), response.getErrorMessage());
                totalErrors++;
            }

            return response;

        } catch (Exception e) {
            String error = "Ошибка выполнения команды " + command.getCommandType() + ": " + e.getMessage();
            log.error("❌ {}", error, e);
            totalErrors++;
            return TelegramResponse.error(error);
        }
    }

    /**
     * Выполнение запроса (CQRS Read Side)
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public TelegramResponse executeQuery(TelegramQuery query) {
        log.debug("🔍 Выполнение запроса: {} для пользователя: {}",
                query.getQueryType(), query.getUserId());
        log.info("🟢 ДИАГНОСТИКА: TelegramService.executeQuery() вызван - это ПРАВИЛЬНЫЙ путь с UI!");
        log.info("🟢 ДИАГНОСТИКА: Ищем специализированный handler для запроса: {}", query.getQueryType());

        try {
            // Валидация запроса
            query.validate();

            // Поиск обработчика
            TelegramQueryHandler<TelegramQuery> handler = (TelegramQueryHandler<TelegramQuery>) queryHandlers
                    .get(query.getClass());

            if (handler == null || !handler.isEnabled()) {
                String error = "Обработчик запроса не найден или отключен: " + query.getQueryType();
                log.error("❌ {}", error);
                log.error("🚨 ДИАГНОСТИКА: НЕ НАЙДЕН handler для {} - доступные handlers: {}",
                        query.getQueryType(), queryHandlers.keySet());
                totalErrors++;
                return TelegramResponse.error(error);
            }

            log.info("🟢 ДИАГНОСТИКА: НАЙДЕН handler {} для запроса {}", handler.getClass().getSimpleName(),
                    query.getQueryType());

            // Выполнение запроса
            TelegramResponse response = handler.handle(query);

            if (response.isSuccessful()) {
                log.debug("✅ Запрос успешно выполнен: {} для пользователя: {}",
                        query.getQueryType(), query.getUserId());
                log.info("🟢 ДИАГНОСТИКА: Handler вернул успешный ответ с UI данными!");
                totalQueriesProcessed++;
            } else {
                log.warn("⚠️ Запрос выполнен с ошибкой: {} для пользователя: {} - {}",
                        query.getQueryType(), query.getUserId(), response.getErrorMessage());
                totalErrors++;
            }

            return response;

        } catch (Exception e) {
            String error = "Ошибка выполнения запроса " + query.getQueryType() + ": " + e.getMessage();
            log.error("❌ {}", error, e);
            totalErrors++;
            return TelegramResponse.error(error);
        }
    }

    /**
     * Обработка входящего запроса (универсальная точка входа)
     */
    public TelegramResponse processRequest(TelegramRequest request) {
        log.info("📨 Обработка входящего запроса: {} от пользователя: {}",
                request.getRequestType(), request.getUserId());

        try {
            if (request.isCommand()) {
                // TODO: Конвертация TelegramRequest в конкретную команду
                log.warn("⚠️ Конвертация TelegramRequest в команду пока не реализована");
                return TelegramResponse.error("Обработка команд через TelegramRequest пока не поддерживается");
            } else {
                // TODO: Конвертация TelegramRequest в конкретный запрос
                log.warn("⚠️ Конвертация TelegramRequest в запрос пока не реализована");
                return TelegramResponse.error("Обработка запросов через TelegramRequest пока не поддерживается");
            }
        } catch (Exception e) {
            String error = "Ошибка обработки запроса: " + e.getMessage();
            log.error("❌ {}", error, e);
            totalErrors++;
            return TelegramResponse.error(error);
        }
    }

    /**
     * Получить UI Factory для создания компонентов интерфейса
     */
    public TelegramUIFactory ui() {
        return uiFactory;
    }

    /**
     * Выполнить команду и получить ответ с UI компонентами
     */
    public TelegramResponse execute(TelegramCommand command) {
        return executeCommand(command);
    }

    /**
     * Выполнить запрос и получить ответ с UI компонентами
     */
    public TelegramResponse execute(TelegramQuery query) {
        return executeQuery(query);
    }

    /**
     * Регистрация CommandHandlers
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void registerCommandHandlers() {
        Map<String, TelegramCommandHandler> handlers = applicationContext.getBeansOfType(TelegramCommandHandler.class);

        log.info("📝 Найдено CommandHandlers: {}", handlers.size());
        log.info("🔍 ДИАГНОСТИКА КОНФЛИКТОВ (TelegramService): Список всех CommandHandlers:");
        handlers.forEach((beanName, handler) -> {
            log.info("   • Bean: '{}' -> Класс: {} (Пакет: {})",
                    beanName,
                    handler.getClass().getSimpleName(),
                    handler.getClass().getPackage().getName());
        });

        for (Map.Entry<String, TelegramCommandHandler> entry : handlers.entrySet()) {
            try {
                TelegramCommandHandler handler = entry.getValue();
                commandHandlers.put(handler.getCommandType(), handler);

                log.debug("✅ Зарегистрирован CommandHandler: {} -> {}",
                        entry.getKey(), handler.getCommandType().getSimpleName());

            } catch (Exception e) {
                log.error("❌ Ошибка регистрации CommandHandler {}: {}",
                        entry.getKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * Регистрация QueryHandlers
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void registerQueryHandlers() {
        Map<String, TelegramQueryHandler> handlers = applicationContext.getBeansOfType(TelegramQueryHandler.class);

        log.info("📊 Найдено QueryHandlers: {}", handlers.size());

        for (Map.Entry<String, TelegramQueryHandler> entry : handlers.entrySet()) {
            try {
                TelegramQueryHandler handler = entry.getValue();
                queryHandlers.put(handler.getQueryType(), handler);

                log.debug("✅ Зарегистрирован QueryHandler: {} -> {}",
                        entry.getKey(), handler.getQueryType().getSimpleName());

            } catch (Exception e) {
                log.error("❌ Ошибка регистрации QueryHandler {}: {}",
                        entry.getKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * Получение статистики сервиса
     */
    @Transactional(readOnly = true)
    public TelegramServiceStats getStats() {
        return new TelegramServiceStats(
                commandHandlers.size(),
                queryHandlers.size(),
                totalCommandsProcessed,
                totalQueriesProcessed,
                totalErrors,
                commandHandlers.keySet().stream().map(Class::getSimpleName).sorted().toList(),
                queryHandlers.keySet().stream().map(Class::getSimpleName).sorted().toList());
    }

    /**
     * Проверка здоровья сервиса
     */
    @Transactional(readOnly = true)
    public TelegramServiceHealth getHealth() {
        boolean isHealthy = commandHandlers.size() > 0 || queryHandlers.size() > 0;
        String status = isHealthy ? "HEALTHY" : "UNHEALTHY";

        return new TelegramServiceHealth(
                status,
                isHealthy,
                commandHandlers.size(),
                queryHandlers.size(),
                totalCommandsProcessed + totalQueriesProcessed,
                totalErrors);
    }

    /**
     * Статистика TelegramService
     */
    public record TelegramServiceStats(
            int commandHandlersCount,
            int queryHandlersCount,
            long totalCommandsProcessed,
            long totalQueriesProcessed,
            long totalErrors,
            java.util.List<String> registeredCommands,
            java.util.List<String> registeredQueries) {
    }

    /**
     * Здоровье TelegramService
     */
    public record TelegramServiceHealth(
            String status,
            boolean healthy,
            int commandHandlersCount,
            int queryHandlersCount,
            long totalRequestsProcessed,
            long totalErrors) {
    }
}