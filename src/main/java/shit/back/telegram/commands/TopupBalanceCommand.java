package shit.back.telegram.commands;

import java.math.BigDecimal;

/**
 * Команда пополнения баланса
 * 
 * Создана для совместимости с существующими адаптерами
 */
public class TopupBalanceCommand implements TelegramCommand {

    private final Long userId;
    private final BigDecimal amount;
    private final String paymentMethod;
    private final String context;

    public TopupBalanceCommand(Long userId, BigDecimal amount, String paymentMethod) {
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod != null ? paymentMethod : "DEFAULT";
        this.context = "TOPUP";
        validate();
    }

    // Конструктор для простого использования с дефолтным методом платежа
    public TopupBalanceCommand(Long userId, BigDecimal amount) {
        this(userId, amount, "DEFAULT");
    }

    // Совместимость с CallbackQueryAdapter
    public TopupBalanceCommand(Long userId, String amountStr, String paymentMethod) {
        System.err.println(String.format(
                "🔍 ДИАГНОСТИКА: СОЗДАНИЕ TopupBalanceCommand с параметрами: userId=%d, amountStr='%s', paymentMethod='%s'",
                userId, amountStr, paymentMethod));

        this.userId = userId;

        try {
            System.err.println("🔍 ДИАГНОСТИКА: ВЫЗОВ parseAmount() с параметром: '" + amountStr + "'");
            this.amount = parseAmount(amountStr);
            System.err.println("🔍 ДИАГНОСТИКА: parseAmount() успешно завершен, результат: " + this.amount);
        } catch (Exception e) {
            System.err.println("❌ ДИАГНОСТИКА: ИСКЛЮЧЕНИЕ в parseAmount(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        this.paymentMethod = paymentMethod != null ? paymentMethod : "DEFAULT";
        this.context = "TOPUP";

        try {
            System.err.println("🔍 ДИАГНОСТИКА: ВЫЗОВ validate()");
            validate();
            System.err.println("🔍 ДИАГНОСТИКА: validate() успешно завершен");
        } catch (Exception e) {
            System.err.println("❌ ДИАГНОСТИКА: ИСКЛЮЧЕНИЕ в validate(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        System.err.println("🔍 ДИАГНОСТИКА: TopupBalanceCommand успешно создан: " + this.toString());
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
        return CommandPriority.HIGH;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return null; // Для случаев, когда сумма вводится позже
        }

        try {
            String cleanAmount = amountStr.trim()
                    .replace(",", ".")
                    .replaceAll("[^0-9.]", "");
            return new BigDecimal(cleanAmount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный формат суммы: " + amountStr);
        }
    }

    @Override
    public void validate() {
        TelegramCommand.super.validate();

        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("Метод платежа обязателен");
        }

        // amount может быть null для команд "начать пополнение"
        if (amount != null) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Сумма должна быть положительной");
            }

            if (amount.compareTo(new BigDecimal("0.01")) < 0) {
                throw new IllegalArgumentException("Минимальная сумма: 0.01");
            }

            if (amount.compareTo(new BigDecimal("1000000")) > 0) {
                throw new IllegalArgumentException("Максимальная сумма: 1,000,000");
            }
        }
    }

    public boolean hasAmount() {
        return amount != null;
    }

    @Override
    public String toString() {
        return String.format("TopupBalanceCommand{userId=%d, amount=%s, paymentMethod='%s'}",
                userId, amount, paymentMethod);
    }
}