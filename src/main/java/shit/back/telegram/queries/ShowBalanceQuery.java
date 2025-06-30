package shit.back.telegram.queries;

/**
 * Запрос на просмотр баланса пользователя
 * 
 * Мигрирован из application.telegram.commands
 */
public class ShowBalanceQuery implements TelegramQuery {

    private final Long userId;
    private final boolean includeHistory;
    private final boolean includeStatistics;

    public ShowBalanceQuery(Long userId, boolean includeHistory, boolean includeStatistics) {
        this.userId = userId;
        this.includeHistory = includeHistory;
        this.includeStatistics = includeStatistics;
        validate();
    }

    public ShowBalanceQuery(Long userId, boolean includeHistory) {
        this(userId, includeHistory, false);
    }

    public ShowBalanceQuery(Long userId) {
        this(userId, false, false);
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public String getContext() {
        return "BALANCE_VIEW";
    }

    @Override
    public QueryPriority getPriority() {
        return QueryPriority.HIGH; // Просмотр баланса имеет высокий приоритет
    }

    @Override
    public boolean isCacheable() {
        return true; // Баланс можно кэшировать на короткое время
    }

    @Override
    public long getCacheTtlSeconds() {
        return 30; // Кэш на 30 секунд
    }

    public boolean isIncludeHistory() {
        return includeHistory;
    }

    public boolean isIncludeStatistics() {
        return includeStatistics;
    }

    public boolean hasExtendedInfo() {
        return includeHistory || includeStatistics;
    }

    @Override
    public String toString() {
        return String.format("ShowBalanceQuery{userId=%d, includeHistory=%s, includeStatistics=%s}",
                userId, includeHistory, includeStatistics);
    }
}