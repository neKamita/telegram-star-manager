package shit.back.telegram.commands.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.service.UserSessionUnifiedService;
import shit.back.telegram.commands.ProcessCustomAmountCommand;
import shit.back.telegram.commands.TelegramCommandHandler;
import shit.back.telegram.dto.TelegramResponse;

/**
 * Обработчик команды обработки пользовательской суммы
 * 
 * Мигрирован из application.telegram.handlers
 */
@Component
@Slf4j
public class ProcessCustomAmountCommandHandler implements TelegramCommandHandler<ProcessCustomAmountCommand> {

    @Autowired
    private UserSessionUnifiedService sessionService;

    public ProcessCustomAmountCommandHandler() {
        log.warn("🟢 ДИАГНОСТИКА: Создан ProcessCustomAmountCommandHandler из TELEGRAM пакета (НОВЫЙ DDD)");
        log.warn("🟢 Путь: {}", this.getClass().getPackage().getName());
    }

    @Override
    @Transactional
    public TelegramResponse handle(ProcessCustomAmountCommand command) throws Exception {
        log.info("🔢 Обработка пользовательской суммы: userId={}, input='{}', context={}",
                command.getUserId(), command.getUserInput(), command.getContext());

        try {
            // Валидация команды (включает парсинг суммы)
            command.validate();

            // Обновляем состояние сессии в зависимости от контекста
            updateUserSessionForContext(command);

            // Формируем ответ в зависимости от контекста
            String responseMessage = formatResponseMessage(command);

            log.info("✅ Пользовательская сумма успешно обработана: userId={}, amount={}, context={}",
                    command.getUserId(), command.getParsedAmount(), command.getContext());

            return TelegramResponse.successWithUI(
                    responseMessage,
                    "CUSTOM_AMOUNT_PROCESSED",
                    command.getParsedAmount());

        } catch (IllegalArgumentException e) {
            log.warn("❌ Некорректная сумма от пользователя {}: {}",
                    command.getUserId(), e.getMessage());
            return TelegramResponse.error(
                    "❌ " + e.getMessage() + "\n\nПожалуйста, введите корректную сумму (например: 100 или 50.25)");

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке пользовательской суммы для пользователя {}: {}",
                    command.getUserId(), e.getMessage(), e);
            return TelegramResponse.error("Не удалось обработать введенную сумму: " + e.getMessage());
        }
    }

    @Override
    public Class<ProcessCustomAmountCommand> getCommandType() {
        return ProcessCustomAmountCommand.class;
    }

    @Override
    public int getHandlerPriority() {
        return 50; // Высокий приоритет для пользовательского ввода
    }

    @Override
    public String getDescription() {
        return "Обработчик пользовательских сумм с валидацией и контекстной обработкой";
    }

    /**
     * Обновление состояния сессии в зависимости от контекста
     */
    private void updateUserSessionForContext(ProcessCustomAmountCommand command) {
        try {
            shit.back.model.UserSession.SessionState newState;

            switch (command.getContext().toLowerCase()) {
                case "topup":
                case "пополнение":
                    newState = shit.back.model.UserSession.SessionState.TOPPING_UP_BALANCE;
                    break;
                case "payment":
                case "оплата":
                    newState = shit.back.model.UserSession.SessionState.SELECTING_PAYMENT_TYPE;
                    break;
                default:
                    newState = shit.back.model.UserSession.SessionState.ENTERING_CUSTOM_AMOUNT;
                    break;
            }

            sessionService.updateSessionState(command.getUserId(), newState);

        } catch (Exception e) {
            log.warn("⚠️ Не удалось обновить состояние сессии для пользователя {}: {}",
                    command.getUserId(), e.getMessage());
            // Не прерываем выполнение из-за ошибки сессии
        }
    }

    /**
     * Форматирование ответного сообщения в зависимости от контекста
     */
    private String formatResponseMessage(ProcessCustomAmountCommand command) {
        StringBuilder message = new StringBuilder();

        message.append("✅ <b>Сумма принята!</b>\n\n");
        message.append("💰 <b>Введенная сумма:</b> ").append(command.getParsedAmount())
                .append(" ").append(command.getCurrency()).append("\n\n");

        // Контекстно-зависимое сообщение
        switch (command.getContext().toLowerCase()) {
            case "topup":
            case "пополнение":
                message.append("🔄 <i>Переходим к выбору способа пополнения баланса...</i>\n\n");
                message.append("Выберите удобный способ пополнения:");
                break;

            case "payment":
            case "оплата":
                message.append("💳 <i>Переходим к выбору способа оплаты...</i>\n\n");
                message.append("Выберите способ оплаты для суммы ").append(command.getParsedAmount()).append(" ")
                        .append(command.getCurrency()).append(":");
                break;

            default:
                message.append("📋 <i>Сумма обработана и готова к использованию.</i>");
                break;
        }

        return message.toString();
    }
}