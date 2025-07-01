package shit.back.telegram.commands;

import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.telegram.commands.TelegramCommand;

/**
 * Команда инициации покупки звезд
 *
 * Использует упрощенную архитектуру покупки звезд
 */
public class InitiateStarPurchaseCommand implements TelegramCommand {

    private final Long userId;
    private final Integer starCount;
    private final String preferredPaymentMethod;
    private final Money customAmount;
    private final Currency currency;
    private final boolean confirmPurchase;

    public InitiateStarPurchaseCommand(Long userId, Integer starCount, String preferredPaymentMethod,
            Money customAmount, Currency currency, boolean confirmPurchase) {
        this.userId = userId;
        this.starCount = starCount;
        this.preferredPaymentMethod = preferredPaymentMethod;
        this.customAmount = customAmount;
        this.currency = currency != null ? currency : Currency.defaultCurrency();
        this.confirmPurchase = confirmPurchase;
    }

    public InitiateStarPurchaseCommand(Long userId, Integer starCount, String preferredPaymentMethod) {
        this(userId, starCount, preferredPaymentMethod, null, Currency.defaultCurrency(), false);
    }

    public InitiateStarPurchaseCommand(Long userId, Integer starCount) {
        this(userId, starCount, null, null, Currency.defaultCurrency(), false);
    }

    /**
     * Конструктор для открытия интерфейса покупки без указания конкретного
     * количества звезд
     */
    public InitiateStarPurchaseCommand(Long userId) {
        this(userId, null, null, null, Currency.defaultCurrency(), false);
    }

    public InitiateStarPurchaseCommand(Long userId, Money customAmount, Currency currency) {
        this(userId, null, null, customAmount, currency, false);
    }

    /**
     * Конструктор для подтверждения покупки с указанным количеством звезд
     */
    public InitiateStarPurchaseCommand(Long userId, Integer starCount, boolean confirmPurchase) {
        this(userId, starCount, null, null, Currency.defaultCurrency(), confirmPurchase);
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    public Integer getStarCount() {
        return starCount;
    }

    public String getPreferredPaymentMethod() {
        return preferredPaymentMethod;
    }

    public Money getCustomAmount() {
        return customAmount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean isConfirmPurchase() {
        return confirmPurchase;
    }

    public boolean hasStarCount() {
        return starCount != null && starCount > 0;
    }

    public boolean hasCustomAmount() {
        return customAmount != null && customAmount.isPositive();
    }

    public boolean hasPreferredPaymentMethod() {
        return preferredPaymentMethod != null && !preferredPaymentMethod.trim().isEmpty();
    }

    @Override
    public String getCommandType() {
        return "INITIATE_STAR_PURCHASE";
    }

    @Override
    public String getContext() {
        return "STAR_PURCHASE";
    }

    @Override
    public void validate() {
        TelegramCommand.super.validate();

        // Для случая открытия интерфейса покупки без конкретных параметров - валидация
        // не требуется
        if (!hasStarCount() && !hasCustomAmount() && !confirmPurchase) {
            // Это валидный случай - пользователь хочет открыть интерфейс выбора пакетов
            return;
        }

        // Если указаны конкретные параметры - проверяем их
        if (!hasStarCount() && !hasCustomAmount() && confirmPurchase) {
            throw new IllegalArgumentException(
                    "Необходимо указать количество звезд или сумму для подтверждения покупки");
        }

        if (hasStarCount() && starCount <= 0) {
            throw new IllegalArgumentException("Количество звезд должно быть положительным");
        }

        if (hasStarCount() && starCount > 10000) {
            throw new IllegalArgumentException("Максимальное количество звезд: 10000");
        }

        if (hasCustomAmount() && !customAmount.isPositive()) {
            throw new IllegalArgumentException("Сумма должна быть положительной");
        }

        if (currency == null) {
            throw new IllegalArgumentException("Валюта обязательна");
        }

        // Валидация способа оплаты если указан
        if (hasPreferredPaymentMethod()) {
            if (!preferredPaymentMethod.matches("^(CARD|CRYPTO|BANK_TRANSFER|FRAGMENT)$")) {
                throw new IllegalArgumentException("Неподдерживаемый способ оплаты: " + preferredPaymentMethod);
            }
        }
    }

    /**
     * Получить тип операции для стратегии
     */
    public String getOperationType() {
        if (confirmPurchase) {
            return "PURCHASE_CONFIRMATION";
        } else if (hasStarCount() || hasCustomAmount()) {
            // ИСПРАВЛЕНИЕ: При выборе пакета звезд нужно показать подтверждение, а не
            // проверять баланс
            return "PURCHASE_CONFIRMATION";
        } else {
            // Случай когда пользователь просто хочет открыть интерфейс покупки
            return "PURCHASE_INTERFACE";
        }
    }

    /**
     * Создать команду подтверждения
     */
    public InitiateStarPurchaseCommand withConfirmation() {
        return new InitiateStarPurchaseCommand(userId, starCount, preferredPaymentMethod,
                customAmount, currency, true);
    }

    /**
     * Получить эффективное количество звезд (из starCount или рассчитанное из
     * суммы)
     */
    public Integer getEffectiveStarCount() {
        if (hasStarCount()) {
            return starCount;
        } else if (hasCustomAmount()) {
            // Примерный расчет: 1 звезда = 0.01 единицы валюты
            return customAmount.getAmount().multiply(java.math.BigDecimal.valueOf(100)).intValue();
        }
        return null;
    }

    /**
     * Получить эффективную сумму (из customAmount или рассчитанную из starCount)
     */
    public Money getEffectiveAmount() {
        if (hasCustomAmount()) {
            return customAmount;
        } else if (hasStarCount()) {
            // Примерный расчет: 1 звезда = 0.01 единицы валюты
            return Money.of(java.math.BigDecimal.valueOf(starCount * 0.01));
        }
        return Money.zero();
    }

    @Override
    public String toString() {
        return String.format("InitiateStarPurchaseCommand{userId=%d, stars=%s, amount=%s, method=%s, confirm=%s}",
                userId, starCount, customAmount, preferredPaymentMethod, confirmPurchase);
    }
}