package shit.back.telegram.commands;

import java.math.BigDecimal;

/**
 * Команда перевода средств между балансами
 * 
 * Обеспечивает перевод с банковского баланса на основной баланс
 * для последующей покупки звезд через Fragment API
 */
public class TransferFundsCommand implements TelegramCommand {

    private final Long userId;
    private final BigDecimal amount;
    private final String sourceBalanceType;
    private final String context;

    /**
     * Основной конструктор для перевода с указанной суммой
     */
    public TransferFundsCommand(Long userId, BigDecimal amount, String sourceBalanceType) {
        this.userId = userId;
        this.amount = amount;
        this.sourceBalanceType = sourceBalanceType != null ? sourceBalanceType.toUpperCase() : "BANK";
        this.context = "TRANSFER_FUNDS";
        validate();
    }

    /**
     * Конструктор для инициации процесса перевода без указания суммы
     */
    public TransferFundsCommand(Long userId, String sourceBalanceType) {
        this(userId, null, sourceBalanceType);
    }

    /**
     * Упрощенный конструктор с банковским балансом по умолчанию
     */
    public TransferFundsCommand(Long userId, BigDecimal amount) {
        this(userId, amount, "BANK");
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

    public String getSourceBalanceType() {
        return sourceBalanceType;
    }

    public boolean hasAmount() {
        return amount != null;
    }

    @Override
    public void validate() {
        TelegramCommand.super.validate();

        if (sourceBalanceType == null || sourceBalanceType.trim().isEmpty()) {
            throw new IllegalArgumentException("Тип исходного баланса обязателен");
        }

        // Проверяем поддерживаемые типы балансов
        if (!"BANK".equals(sourceBalanceType) && !"MAIN".equals(sourceBalanceType)) {
            throw new IllegalArgumentException("Неподдерживаемый тип баланса: " + sourceBalanceType);
        }

        // Пока поддерживаем только перевод с BANK на MAIN
        if (!"BANK".equals(sourceBalanceType)) {
            throw new IllegalArgumentException("Перевод возможен только с банковского баланса");
        }

        // Валидация суммы, если указана
        if (amount != null) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Сумма перевода должна быть положительной");
            }

            if (amount.compareTo(new BigDecimal("0.01")) < 0) {
                throw new IllegalArgumentException("Минимальная сумма перевода: 0.01");
            }

            if (amount.compareTo(new BigDecimal("1000000")) > 0) {
                throw new IllegalArgumentException("Максимальная сумма перевода: 1,000,000");
            }
        }
    }

    @Override
    public String toString() {
        return String.format("TransferFundsCommand{userId=%d, amount=%s, sourceType='%s'}",
                userId, amount, sourceBalanceType);
    }
}