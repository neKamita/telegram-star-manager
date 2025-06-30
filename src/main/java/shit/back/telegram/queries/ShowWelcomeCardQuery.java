package shit.back.telegram.queries;

import shit.back.domain.balance.valueobjects.Currency;
/**
 * Запрос приветственной карточки
 *
 * Интегрируется с WelcomeCardStrategy для отображения приветствия
 */
public class ShowWelcomeCardQuery implements TelegramQuery {

    private final Long userId;
    private final boolean includeBalance;
    private final String cardType;
    private final String userName;
    private final Currency currency;

    public ShowWelcomeCardQuery(Long userId, boolean includeBalance, String cardType,
            String userName, Currency currency) {
        this.userId = userId;
        this.includeBalance = includeBalance;
        this.cardType = cardType != null ? cardType : "USER_WELCOME_CARD";
        this.userName = userName != null ? userName : "пользователь";
        this.currency = currency != null ? currency : Currency.defaultCurrency();
    }

    public ShowWelcomeCardQuery(Long userId, boolean includeBalance, String cardType, String userName) {
        this(userId, includeBalance, cardType, userName, Currency.defaultCurrency());
    }

    public ShowWelcomeCardQuery(Long userId, String cardType, String userName) {
        this(userId, true, cardType, userName, Currency.defaultCurrency());
    }

    public ShowWelcomeCardQuery(Long userId, String userName) {
        this(userId, true, "USER_WELCOME_CARD", userName, Currency.defaultCurrency());
    }

    public ShowWelcomeCardQuery(Long userId) {
        this(userId, true, "USER_WELCOME_CARD", "пользователь", Currency.defaultCurrency());
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    public boolean isIncludeBalance() {
        return includeBalance;
    }

    public String getCardType() {
        return cardType;
    }

    public String getUserName() {
        return userName;
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean isOnboardingCard() {
        return "ONBOARDING_CARD".equals(cardType);
    }

    public boolean isPaymentMethodsCard() {
        return "PAYMENT_METHODS_CARD".equals(cardType);
    }

    public boolean isUserWelcomeCard() {
        return "USER_WELCOME_CARD".equals(cardType);
    }

    @Override
    public String getQueryType() {
        return "SHOW_WELCOME_CARD";
    }

    @Override
    public String getContext() {
        return "WELCOME_CARD";
    }

    @Override
    public boolean isCacheable() {
        return includeBalance; // Кэшируем только если включен баланс
    }

    @Override
    public void validate() {
        TelegramQuery.super.validate();

        if (cardType == null || cardType.trim().isEmpty()) {
            throw new IllegalArgumentException("Тип карточки обязателен");
        }

        // Валидация поддерживаемых типов карточек
        if (!cardType.matches("^(USER_WELCOME_CARD|PAYMENT_METHODS_CARD|ONBOARDING_CARD)$")) {
            throw new IllegalArgumentException("Неподдерживаемый тип карточки: " + cardType);
        }

        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя пользователя обязательно");
        }

        if (currency == null) {
            throw new IllegalArgumentException("Валюта обязательна");
        }
    }

    /**
     * Создать данные для WelcomeCardStrategy
     */
    public Object createStrategyData() {
        if (isOnboardingCard()) {
            return userName;
        } else if (isPaymentMethodsCard()) {
            return currency;
        } else {
            // Для USER_WELCOME_CARD нужно будет добавить DualBalanceResponse в обработчике
            return userName;
        }
    }

    @Override
    public String toString() {
        return String.format("ShowWelcomeCardQuery{userId=%d, type=%s, user=%s, includeBalance=%s}",
                userId, cardType, userName, includeBalance);
    }
}