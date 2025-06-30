package shit.back.telegram.queries;

import shit.back.domain.balance.valueobjects.Currency;
/**
 * Расширенный запрос баланса с UI опциями
 *
 * Интегрируется с BalanceDisplayStrategy для создания богатого UI
 */
public class ShowRichBalanceQuery implements TelegramQuery {

    private final Long userId;
    private final boolean includeHistory;
    private final boolean includeStatistics;
    private final String displayFormat;
    private final Currency currency;

    public ShowRichBalanceQuery(Long userId, boolean includeHistory, boolean includeStatistics,
            String displayFormat, Currency currency) {
        this.userId = userId;
        this.includeHistory = includeHistory;
        this.includeStatistics = includeStatistics;
        this.displayFormat = displayFormat != null ? displayFormat : "DUAL_BALANCE_INFO";
        this.currency = currency;
    }

    public ShowRichBalanceQuery(Long userId, boolean includeHistory, boolean includeStatistics, String displayFormat) {
        this(userId, includeHistory, includeStatistics, displayFormat, Currency.defaultCurrency());
    }

    public ShowRichBalanceQuery(Long userId, String displayFormat) {
        this(userId, false, false, displayFormat, Currency.defaultCurrency());
    }

    public ShowRichBalanceQuery(Long userId) {
        this(userId, false, false, "DUAL_BALANCE_INFO", Currency.defaultCurrency());
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public String getQueryType() {
        return "SHOW_RICH_BALANCE";
    }

    public boolean isIncludeHistory() {
        return includeHistory;
    }

    public boolean isIncludeStatistics() {
        return includeStatistics;
    }

    public String getDisplayFormat() {
        return displayFormat;
    }

    public Currency getCurrency() {
        return currency;
    }

    @Override
    public String getContext() {
        return "RICH_BALANCE";
    }

    @Override
    public boolean isCacheable() {
        return true; // Кэшируем баланс для производительности
    }

    @Override
    public void validate() {
        TelegramQuery.super.validate();

        if (displayFormat == null || displayFormat.trim().isEmpty()) {
            throw new IllegalArgumentException("Формат отображения обязателен");
        }

        // Валидация поддерживаемых форматов
        if (!displayFormat.matches("^(DUAL_BALANCE_INFO|BALANCE_SUMMARY|BALANCE_DETAILS)$")) {
            throw new IllegalArgumentException("Неподдерживаемый формат отображения: " + displayFormat);
        }
    }

    @Override
    public String toString() {
        return String.format("ShowRichBalanceQuery{userId=%d, format=%s, history=%s, stats=%s}",
                userId, displayFormat, includeHistory, includeStatistics);
    }
}