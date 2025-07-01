package shit.back.telegram.queries;

import shit.back.entity.TransactionStatus;

/**
 * Запрос истории покупок звезд с пагинацией
 *
 * Интегрируется с PurchaseHistoryStrategy для отображения истории
 */
public class ShowPurchaseHistoryQuery implements TelegramQuery {

    private final Long userId;
    private final int page;
    private final int limit;
    private final String filterBy;
    private final TransactionStatus statusFilter;

    public ShowPurchaseHistoryQuery(Long userId, int page, int limit, String filterBy, TransactionStatus statusFilter) {
        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Fail-Fast валидация параметров конструктора
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId должен быть положительным числом");
        }

        this.userId = userId;
        this.page = Math.max(0, page); // ИСПРАВЛЕНО: Минимум 0 (защита от отрицательных значений)
        this.limit = Math.min(Math.max(1, limit), 50); // ИСПРАВЛЕНО: От 1 до 50 (граничные случаи)
        this.filterBy = filterBy != null ? filterBy : "ALL"; // ИСПРАВЛЕНО: Дефолтное значение
        this.statusFilter = statusFilter;
    }

    public ShowPurchaseHistoryQuery(Long userId, int page, int limit, String filterBy) {
        this(userId, page, limit, filterBy, null);
    }

    public ShowPurchaseHistoryQuery(Long userId, int page, int limit) {
        this(userId, page, limit, "ALL", null);
    }

    public ShowPurchaseHistoryQuery(Long userId) {
        this(userId, 0, 10, "ALL", null);
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    public int getPage() {
        return page;
    }

    public int getLimit() {
        return limit;
    }

    public String getFilterBy() {
        return filterBy;
    }

    public TransactionStatus getStatusFilter() {
        return statusFilter;
    }

    public boolean hasStatusFilter() {
        return statusFilter != null;
    }

    public boolean isRecentOnly() {
        return "RECENT".equals(filterBy);
    }

    public boolean isSuccessfulOnly() {
        return "SUCCESSFUL".equals(filterBy);
    }

    public boolean isFailedOnly() {
        return "FAILED".equals(filterBy);
    }

    @Override
    public String getQueryType() {
        return "SHOW_PURCHASE_HISTORY";
    }

    @Override
    public String getContext() {
        return "PURCHASE_HISTORY";
    }

    @Override
    public boolean isCacheable() {
        return true; // Кэшируем историю
    }

    @Override
    public void validate() {
        TelegramQuery.super.validate();

        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Улучшенная валидация с Fail-Fast принципом
        if (userId == null) {
            throw new IllegalArgumentException("UserId не может быть null");
        }

        if (userId <= 0) {
            throw new IllegalArgumentException("UserId должен быть положительным числом");
        }

        if (page < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        }

        // ИСПРАВЛЕНИЕ: Дополнительная проверка разумного максимума страниц
        if (page > 999) {
            throw new IllegalArgumentException("Номер страницы слишком большой (максимум: 999)");
        }

        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Лимит должен быть от 1 до 50");
        }

        // ИСПРАВЛЕНИЕ: Более строгая валидация фильтров
        if (filterBy == null || filterBy.trim().isEmpty()) {
            throw new IllegalArgumentException("Тип фильтра не может быть пустым");
        }

        if (!filterBy.matches("^(ALL|RECENT|SUCCESSFUL|FAILED|BY_STATUS)$")) {
            throw new IllegalArgumentException("Неподдерживаемый тип фильтра: " + filterBy);
        }

        if ("BY_STATUS".equals(filterBy) && statusFilter == null) {
            throw new IllegalArgumentException("При фильтре BY_STATUS необходимо указать статус");
        }
    }

    /**
     * Получить offset для базы данных
     */
    public int getOffset() {
        return page * limit;
    }

    /**
     * Определить тип контента для стратегии
     */
    public String getContentType() {
        if (isRecentOnly()) {
            return "RECENT_PURCHASES";
        }
        return "STAR_PURCHASE_HISTORY";
    }

    @Override
    public String toString() {
        return String.format("ShowPurchaseHistoryQuery{userId=%d, page=%d, limit=%d, filter=%s, status=%s}",
                userId, page, limit, filterBy, statusFilter);
    }
}