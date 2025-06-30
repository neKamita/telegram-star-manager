package shit.back.telegram.commands;

import java.math.BigDecimal;

/**
 * Команда на обработку пользовательской суммы
 * 
 * Мигрирована из application.telegram.commands
 */
public class ProcessCustomAmountCommand implements TelegramCommand {

    private final Long userId;
    private final String userInput;
    private final BigDecimal parsedAmount;
    private final String currency;
    private final String context;

    public ProcessCustomAmountCommand(Long userId, String userInput, String context) {
        this.userId = userId;
        this.userInput = userInput;
        this.context = context != null ? context : "DEFAULT";
        this.currency = "USD"; // По умолчанию
        this.parsedAmount = parseAmount(userInput);
        validate();
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public String getContext() {
        return context;
    }

    @Override
    public CommandPriority getPriority() {
        return CommandPriority.HIGH; // Пользовательский ввод имеет высокий приоритет
    }

    public String getUserInput() {
        return userInput;
    }

    public BigDecimal getParsedAmount() {
        return parsedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    private BigDecimal parseAmount(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Сумма не может быть пустой");
        }

        try {
            // Очищаем от лишних символов и заменяем запятую на точку
            String cleanInput = input.trim()
                    .replace(",", ".")
                    .replaceAll("[^0-9.]", "");

            return new BigDecimal(cleanInput);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный формат суммы: " + input);
        }
    }

    @Override
    public void validate() {
        TelegramCommand.super.validate();

        if (userInput == null || userInput.trim().isEmpty()) {
            throw new IllegalArgumentException("Пользовательский ввод не может быть пустым");
        }

        if (parsedAmount == null || parsedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма должна быть положительной");
        }

        if (parsedAmount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException("Минимальная сумма: 0.01");
        }

        if (parsedAmount.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("Максимальная сумма: 1,000,000");
        }

        if (context == null || context.trim().isEmpty()) {
            throw new IllegalArgumentException("Контекст команды обязателен");
        }
    }

    @Override
    public String toString() {
        return String.format("ProcessCustomAmountCommand{userId=%d, userInput='%s', parsedAmount=%s, context='%s'}",
                userId, userInput, parsedAmount, context);
    }
}