package shit.back.domain.starPurchase.exceptions;

import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.starPurchase.valueobjects.FragmentTransactionId;
import shit.back.domain.starPurchase.valueobjects.StarPurchaseId;

/**
 * Доменное исключение для ошибок покупки звезд через Fragment API
 * 
 * Инкапсулирует бизнес-логику обработки ошибок покупки звезд
 * и предоставляет детальную информацию об ошибке.
 */
public class StarPurchaseFailedException extends StarPurchaseDomainException {

    private final StarPurchaseId starPurchaseId;
    private final FragmentTransactionId fragmentTransactionId;
    private final Long userId;
    private final Money requestedAmount;
    private final Integer requestedStars;
    private final String failureReason;
    private final String fragmentErrorCode;

    /**
     * Конструктор с полной информацией о неудачной покупке
     */
    public StarPurchaseFailedException(StarPurchaseId starPurchaseId, FragmentTransactionId fragmentTransactionId,
            Long userId, Money requestedAmount, Integer requestedStars,
            String failureReason, String fragmentErrorCode) {
        super(buildMessage(starPurchaseId, fragmentTransactionId, userId, requestedAmount, requestedStars,
                failureReason));
        this.starPurchaseId = starPurchaseId;
        this.fragmentTransactionId = fragmentTransactionId;
        this.userId = userId;
        this.requestedAmount = requestedAmount;
        this.requestedStars = requestedStars;
        this.failureReason = failureReason;
        this.fragmentErrorCode = fragmentErrorCode;
    }

    /**
     * Упрощенный конструктор для основных ошибок
     */
    public StarPurchaseFailedException(StarPurchaseId starPurchaseId, Long userId,
            String failureReason, String fragmentErrorCode) {
        super(buildMessage(starPurchaseId, null, userId, null, null, failureReason));
        this.starPurchaseId = starPurchaseId;
        this.fragmentTransactionId = null;
        this.userId = userId;
        this.requestedAmount = null;
        this.requestedStars = null;
        this.failureReason = failureReason;
        this.fragmentErrorCode = fragmentErrorCode;
    }

    /**
     * Конструктор с причиной исключения
     */
    public StarPurchaseFailedException(StarPurchaseId starPurchaseId, String failureReason, Throwable cause) {
        super(buildMessage(starPurchaseId, null, null, null, null, failureReason), cause);
        this.starPurchaseId = starPurchaseId;
        this.fragmentTransactionId = null;
        this.userId = null;
        this.requestedAmount = null;
        this.requestedStars = null;
        this.failureReason = failureReason;
        this.fragmentErrorCode = null;
    }

    /**
     * Построение сообщения об ошибке
     */
    private static String buildMessage(StarPurchaseId starPurchaseId, FragmentTransactionId fragmentTransactionId,
            Long userId, Money requestedAmount, Integer requestedStars, String failureReason) {
        StringBuilder message = new StringBuilder();
        message.append("Ошибка покупки звезд");

        if (starPurchaseId != null) {
            message.append(" (ID: ").append(starPurchaseId.getShortValue()).append(")");
        }

        if (userId != null) {
            message.append(" для пользователя ").append(userId);
        }

        if (requestedAmount != null && requestedStars != null) {
            message.append(": ").append(requestedStars).append(" звезд за ")
                    .append(requestedAmount.getFormattedAmount());
        }

        if (fragmentTransactionId != null) {
            message.append(" (Fragment TX: ").append(fragmentTransactionId.getShortValue()).append(")");
        }

        message.append(". Причина: ").append(failureReason);

        return message.toString();
    }

    /**
     * Получение ID покупки звезд
     */
    public StarPurchaseId getStarPurchaseId() {
        return starPurchaseId;
    }

    /**
     * Получение ID Fragment транзакции
     */
    public FragmentTransactionId getFragmentTransactionId() {
        return fragmentTransactionId;
    }

    /**
     * Получение ID пользователя
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Получение запрошенной суммы
     */
    public Money getRequestedAmount() {
        return requestedAmount;
    }

    /**
     * Получение количества запрошенных звезд
     */
    public Integer getRequestedStars() {
        return requestedStars;
    }

    /**
     * Получение причины неудачи
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * Получение кода ошибки Fragment
     */
    public String getFragmentErrorCode() {
        return fragmentErrorCode;
    }

    /**
     * Проверка, является ли ошибка связанной с Fragment API
     */
    public boolean isFragmentApiError() {
        return fragmentErrorCode != null && !fragmentErrorCode.trim().isEmpty();
    }

    /**
     * Проверка, является ли ошибка связанной с недостатком средств
     */
    public boolean isInsufficientFundsError() {
        return failureReason != null && failureReason.toLowerCase().contains("insufficient");
    }

    /**
     * Проверка, является ли ошибка временной (можно повторить попытку)
     */
    public boolean isRetryable() {
        if (fragmentErrorCode == null) {
            return false;
        }
        // Временные ошибки Fragment API
        return fragmentErrorCode.equals("TEMPORARY_ERROR") ||
                fragmentErrorCode.equals("RATE_LIMITED") ||
                fragmentErrorCode.equals("SERVICE_UNAVAILABLE");
    }

    @Override
    public String getErrorCode() {
        if (isFragmentApiError()) {
            return "STAR_PURCHASE_FRAGMENT_ERROR";
        } else if (isInsufficientFundsError()) {
            return "STAR_PURCHASE_INSUFFICIENT_FUNDS";
        } else {
            return "STAR_PURCHASE_FAILED";
        }
    }

    @Override
    public String getUserFriendlyMessage() {
        if (isInsufficientFundsError()) {
            return "Недостаточно средств для покупки звезд";
        } else if (isFragmentApiError()) {
            return "Временная ошибка сервиса Telegram. Попробуйте позже";
        } else {
            return "Не удалось выполнить покупку звезд: " + failureReason;
        }
    }

    @Override
    public boolean isCritical() {
        // Критическими считаем ошибки, не связанные с временными проблемами
        return !isRetryable() && !isInsufficientFundsError();
    }
}