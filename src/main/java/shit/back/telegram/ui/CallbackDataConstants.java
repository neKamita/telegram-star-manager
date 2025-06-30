package shit.back.telegram.ui;

import java.util.regex.Pattern;

/**
 * Константы для callback data в Telegram интерфейсе
 * 
 * Централизованное управление всеми callback данными с валидацией
 * и генерацией структурированных callback строк
 */
public final class CallbackDataConstants {

    private CallbackDataConstants() {
        // Утилитарный класс - запрещаем создание экземпляров
    }

    // Основные префиксы команд
    public static final String BALANCE_PREFIX = "balance";
    public static final String TOPUP_PREFIX = "topup";
    public static final String STARS_PREFIX = "stars";
    public static final String PAYMENT_PREFIX = "payment";
    public static final String HISTORY_PREFIX = "history";
    public static final String TRANSFER_PREFIX = "transfer";
    public static final String CONFIRM_PREFIX = "confirm";
    public static final String CANCEL_PREFIX = "cancel";
    public static final String MENU_PREFIX = "menu";
    public static final String HELP_PREFIX = "help";

    // Разделитель в callback data
    public static final String SEPARATOR = ":";

    // === МЕНЮ И НАВИГАЦИЯ ===
    public static final String MAIN_MENU = "menu:main";
    public static final String BALANCE_MENU = "menu:balance";
    public static final String STARS_MENU = "menu:stars";
    public static final String HELP_MENU = "menu:help";

    // === БАЛАНС ===
    public static final String SHOW_BALANCE = "balance:show";
    public static final String BALANCE_DETAILS = "balance:details";
    public static final String BALANCE_SUMMARY = "balance:summary";
    public static final String BALANCE_HISTORY = "balance:history";

    // === ПОПОЛНЕНИЕ БАЛАНСА ===
    public static final String TOPUP_BALANCE = "topup:start";
    public static final String TOPUP_CUSTOM = "topup:custom";
    public static final String TOPUP_METHOD = "topup:method";

    // === СПОСОБЫ ОПЛАТЫ ===
    public static final String PAYMENT_TON = "payment:ton";
    public static final String PAYMENT_YOOKASSA = "payment:yookassa";
    public static final String PAYMENT_UZS = "payment:uzs";

    // === НОВЫЕ CALLBACK'Ы ДЛЯ ПОПОЛНЕНИЯ ===
    public static final String PAYMENT_CRYPTO = "payment_crypto";
    public static final String PAYMENT_YOOMONEY = "payment_yoomoney";
    public static final String PAYMENT_UZS_SIMPLE = "payment_uzs";

    // Предустановленные суммы пополнения
    public static final String TOPUP_AMOUNT_10 = "topup_amount_10";
    public static final String TOPUP_AMOUNT_25 = "topup_amount_25";
    public static final String TOPUP_AMOUNT_50 = "topup_amount_50";
    public static final String TOPUP_AMOUNT_100 = "topup_amount_100";
    public static final String TOPUP_AMOUNT_250 = "topup_amount_250";
    public static final String TOPUP_AMOUNT_500 = "topup_amount_500";

    // Пользовательская сумма
    public static final String CUSTOM_AMOUNT = "custom_amount";

    // ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #2: Константы для подтверждения пополнения
    public static final String CONFIRM_TOPUP_10 = "confirm_topup_10";
    public static final String CONFIRM_TOPUP_25 = "confirm_topup_25";
    public static final String CONFIRM_TOPUP_50 = "confirm_topup_50";
    public static final String CONFIRM_TOPUP_100 = "confirm_topup_100";
    public static final String CONFIRM_TOPUP_250 = "confirm_topup_250";
    public static final String CONFIRM_TOPUP_500 = "confirm_topup_500";

    // ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #2: Дополнительные константы для больших сумм и
    // пользовательских сумм
    public static final String CONFIRM_TOPUP_1000 = "confirm_topup_1000";
    public static final String CONFIRM_TOPUP_2000 = "confirm_topup_2000";
    public static final String CONFIRM_TOPUP_CUSTOM = "confirm_topup_custom";

    // Отмена операций
    public static final String CANCEL_TOPUP = "cancel_topup";

    // === ПОКУПКА ЗВЕЗД ===
    public static final String BUY_STARS = "stars:buy";
    public static final String STARS_PACKAGE = "stars:package";
    public static final String STARS_CUSTOM = "stars:custom";
    public static final String STARS_CONFIRM = "stars:confirm";

    // === РАСШИРЕННЫЕ UI CALLBACK'Ы ===
    // Богатый баланс
    public static final String RICH_BALANCE_PREFIX = "rich_balance";
    public static final String BALANCE_ACTIONS_PREFIX = "balance_actions";

    // История покупок
    public static final String PURCHASE_HISTORY_PREFIX = "purchase_history";
    public static final String HISTORY_NAV_PREFIX = "history_nav";

    // Приветственные карточки
    public static final String WELCOME_CARD_PREFIX = "welcome_card";
    public static final String GET_STARTED = "get_started";

    // Покупка звезд (расширенные)
    public static final String STAR_PURCHASE_PREFIX = "star_purchase";
    public static final String PURCHASE_PACKAGE_PREFIX = "purchase_package";
    public static final String CONFIRM_PURCHASE_PREFIX = "confirm_purchase";

    // Способы оплаты (расширенные)
    public static final String PAYMENT_METHOD_PREFIX = "payment_method";

    // === ПЕРЕВОД СРЕДСТВ ===
    public static final String TRANSFER_TO_MAIN = "transfer:to_main";
    public static final String TRANSFER_CONFIRM = "transfer:confirm";

    // === ИСТОРИЯ ===
    public static final String HISTORY_PURCHASES = "history:purchases";
    public static final String HISTORY_TOPUPS = "history:topups";
    public static final String HISTORY_TRANSFERS = "history:transfers";
    public static final String HISTORY_PAGE = "history:page";

    // === ПОДТВЕРЖДЕНИЕ И ОТМЕНА ===
    public static final String CONFIRM_YES = "confirm:yes";
    public static final String CONFIRM_NO = "confirm:no";
    public static final String CANCEL_OPERATION = "cancel:operation";

    // === ПОМОЩЬ ===
    public static final String HELP_BALANCE = "help:balance";
    public static final String HELP_STARS = "help:stars";
    public static final String HELP_PAYMENT = "help:payment";
    public static final String HELP_SUPPORT = "help:support";

    /**
     * Генерация callback data для пополнения определенной суммы
     */
    public static String topupAmount(String amount) {
        return TOPUP_PREFIX + SEPARATOR + "amount" + SEPARATOR + amount;
    }

    /**
     * Генерация callback data для выбора способа оплаты
     */
    public static String paymentMethod(String method) {
        return PAYMENT_PREFIX + SEPARATOR + method.toLowerCase();
    }

    /**
     * Генерация callback data для покупки пакета звезд
     */
    public static String starsPackage(int stars, String price) {
        return STARS_PREFIX + SEPARATOR + "package" + SEPARATOR + stars + SEPARATOR + price;
    }

    /**
     * Генерация callback data для подтверждения операции
     */
    public static String confirmOperation(String operationType, String operationId) {
        return CONFIRM_PREFIX + SEPARATOR + operationType + SEPARATOR + operationId;
    }

    /**
     * Генерация callback data для навигации по истории
     */
    public static String historyPage(String historyType, int page) {
        return HISTORY_PREFIX + SEPARATOR + historyType + SEPARATOR + "page" + SEPARATOR + page;
    }

    /**
     * Генерация callback data для перевода средств
     */
    public static String transferAmount(String amount) {
        return TRANSFER_PREFIX + SEPARATOR + "amount" + SEPARATOR + amount;
    }

    /**
     * Генерация callback data для богатого баланса
     */
    public static String richBalance(String displayType) {
        return RICH_BALANCE_PREFIX + SEPARATOR + displayType;
    }

    /**
     * Генерация callback data для действий с балансом
     */
    public static String balanceAction(String action) {
        return BALANCE_ACTIONS_PREFIX + SEPARATOR + action;
    }

    /**
     * Генерация callback data для истории покупок
     */
    public static String purchaseHistory(String filterType, int page) {
        return PURCHASE_HISTORY_PREFIX + SEPARATOR + filterType + SEPARATOR + page;
    }

    /**
     * Генерация callback data для навигации по истории
     */
    public static String historyNavigation(String direction, int page) {
        return HISTORY_NAV_PREFIX + SEPARATOR + direction + SEPARATOR + page;
    }

    /**
     * Генерация callback data для приветственной карточки
     */
    public static String welcomeCard(String cardType) {
        return WELCOME_CARD_PREFIX + SEPARATOR + cardType;
    }

    /**
     * Генерация callback data для покупки звезд
     */
    public static String starPurchase(String action, String parameter) {
        return STAR_PURCHASE_PREFIX + SEPARATOR + action + SEPARATOR + parameter;
    }

    /**
     * Генерация callback data для пакета звезд
     */
    public static String purchasePackage(int stars, String price) {
        return PURCHASE_PACKAGE_PREFIX + SEPARATOR + stars + SEPARATOR + price;
    }

    /**
     * Генерация callback data для подтверждения покупки
     */
    public static String confirmPurchase(String purchaseId) {
        return CONFIRM_PURCHASE_PREFIX + SEPARATOR + purchaseId;
    }

    /**
     * Генерация callback data для способа оплаты
     */
    public static String paymentMethodChoice(String method, String amount) {
        return PAYMENT_METHOD_PREFIX + SEPARATOR + method + SEPARATOR + amount;
    }

    /**
     * Валидация callback data
     */
    public static boolean isValidCallback(String callbackData) {
        if (callbackData == null || callbackData.trim().isEmpty()) {
            return false;
        }

        // Базовая проверка формата
        if (!callbackData.contains(SEPARATOR)) {
            // Простые команды без параметров
            return isSimpleCommand(callbackData);
        }

        String[] parts = callbackData.split(Pattern.quote(SEPARATOR));
        if (parts.length < 2) {
            return false;
        }

        // Проверка известных префиксов
        String prefix = parts[0];
        return isKnownPrefix(prefix);
    }

    /**
     * Извлечение префикса команды из callback data
     */
    public static String extractPrefix(String callbackData) {
        if (callbackData == null || callbackData.trim().isEmpty()) {
            return null;
        }

        if (!callbackData.contains(SEPARATOR)) {
            return callbackData;
        }

        return callbackData.split(Pattern.quote(SEPARATOR))[0];
    }

    /**
     * Извлечение параметров из callback data
     */
    public static String[] extractParams(String callbackData) {
        if (callbackData == null || !callbackData.contains(SEPARATOR)) {
            return new String[0];
        }

        String[] parts = callbackData.split(Pattern.quote(SEPARATOR));
        if (parts.length <= 1) {
            return new String[0];
        }

        String[] params = new String[parts.length - 1];
        System.arraycopy(parts, 1, params, 0, params.length);
        return params;
    }

    /**
     * Проверка простых команд без параметров
     */
    private static boolean isSimpleCommand(String command) {
        return switch (command) {
            case MAIN_MENU, BALANCE_MENU, STARS_MENU, HELP_MENU,
                    SHOW_BALANCE, BALANCE_DETAILS, BALANCE_SUMMARY, BALANCE_HISTORY,
                    TOPUP_BALANCE, TOPUP_CUSTOM, TOPUP_METHOD,
                    PAYMENT_TON, PAYMENT_YOOKASSA, PAYMENT_UZS,
                    // Новые callback'ы для пополнения
                    PAYMENT_CRYPTO, PAYMENT_YOOMONEY, PAYMENT_UZS_SIMPLE,
                    TOPUP_AMOUNT_10, TOPUP_AMOUNT_25, TOPUP_AMOUNT_50,
                    TOPUP_AMOUNT_100, TOPUP_AMOUNT_250, TOPUP_AMOUNT_500,
                    CUSTOM_AMOUNT,
                    // Новые константы для подтверждения
                    CONFIRM_TOPUP_10, CONFIRM_TOPUP_25, CONFIRM_TOPUP_50,
                    CONFIRM_TOPUP_100, CONFIRM_TOPUP_250, CONFIRM_TOPUP_500,
                    CONFIRM_TOPUP_1000, CONFIRM_TOPUP_2000, CONFIRM_TOPUP_CUSTOM,
                    CANCEL_TOPUP,
                    BUY_STARS, STARS_CUSTOM, STARS_CONFIRM,
                    TRANSFER_TO_MAIN, TRANSFER_CONFIRM,
                    HISTORY_PURCHASES, HISTORY_TOPUPS, HISTORY_TRANSFERS,
                    CONFIRM_YES, CONFIRM_NO, CANCEL_OPERATION,
                    HELP_BALANCE, HELP_STARS, HELP_PAYMENT, HELP_SUPPORT ->
                true;
            default -> false;
        };
    }

    /**
     * Проверка известных префиксов
     */
    private static boolean isKnownPrefix(String prefix) {
        return switch (prefix) {
            case BALANCE_PREFIX, TOPUP_PREFIX, STARS_PREFIX, PAYMENT_PREFIX,
                    HISTORY_PREFIX, TRANSFER_PREFIX, CONFIRM_PREFIX, CANCEL_PREFIX,
                    MENU_PREFIX, HELP_PREFIX,
                    RICH_BALANCE_PREFIX, BALANCE_ACTIONS_PREFIX, PURCHASE_HISTORY_PREFIX,
                    HISTORY_NAV_PREFIX, WELCOME_CARD_PREFIX, STAR_PURCHASE_PREFIX,
                    PURCHASE_PACKAGE_PREFIX, CONFIRM_PURCHASE_PREFIX, PAYMENT_METHOD_PREFIX ->
                true;
            default -> false;
        };
    }

    /**
     * Создание callback data для пагинации
     */
    public static String pagination(String baseCallback, int page) {
        return baseCallback + SEPARATOR + "page" + SEPARATOR + page;
    }

    /**
     * Проверка является ли callback командой навигации
     */
    public static boolean isNavigationCallback(String callbackData) {
        return callbackData != null && (callbackData.startsWith(MENU_PREFIX) ||
                callbackData.equals(MAIN_MENU) ||
                callbackData.equals(BALANCE_MENU) ||
                callbackData.equals(STARS_MENU) ||
                callbackData.equals(HELP_MENU));
    }

    /**
     * Проверка является ли callback командой подтверждения
     */
    public static boolean isConfirmationCallback(String callbackData) {
        return callbackData != null && callbackData.startsWith(CONFIRM_PREFIX);
    }

    /**
     * Проверка является ли callback командой отмены
     */
    public static boolean isCancellationCallback(String callbackData) {
        return callbackData != null && callbackData.startsWith(CANCEL_PREFIX);
    }
}