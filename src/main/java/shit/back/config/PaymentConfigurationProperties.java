package shit.back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;

/**
 * Конфигурационные свойства для платежных систем
 *
 * Все конфиденциальные данные должны быть переданы через environment variables
 */
@Data
@ConfigurationProperties(prefix = "payment")
@Validated
public class PaymentConfigurationProperties {

    /**
     * Общие настройки платежной системы
     */
    @NotNull
    private General general = new General();

    /**
     * Настройки TON Wallet
     */
    @NotNull
    private Ton ton = new Ton();

    /**
     * Настройки YooKassa
     */
    @NotNull
    private YooKassa yookassa = new YooKassa();

    /**
     * Настройки Telegram Fragment API
     */
    @NotNull
    private Fragment fragment = new Fragment();

    /**
     * Настройки UZS платежной системы
     */
    @NotNull
    private UzsPayment uzsPayment = new UzsPayment();

    @Data
    public static class General {

        /**
         * Базовый URL для callback'ов
         */
        @NotBlank(message = "Базовый URL не может быть пустым")
        private String callbackBaseUrl = "${PAYMENT_CALLBACK_BASE_URL:http://localhost:8080}";

        /**
         * Время жизни платежа в минутах
         */
        @Positive(message = "Время жизни платежа должно быть положительным")
        private Integer paymentTimeoutMinutes = 30;

        /**
         * Максимальное количество попыток обработки
         */
        @Positive(message = "Максимальное количество попыток должно быть положительным")
        private Integer maxRetryAttempts = 3;

        /**
         * Интервал между попытками в минутах
         */
        @Positive(message = "Интервал между попытками должен быть положительным")
        private Integer retryIntervalMinutes = 5;

        /**
         * Включить логирование всех операций
         */
        private Boolean enableDetailedLogging = true;

        /**
         * Секретный ключ для подписи callback'ов
         */
        @NotBlank(message = "Секретный ключ не может быть пустым")
        private String callbackSecret = "${PAYMENT_CALLBACK_SECRET}";
    }

    @Data
    public static class Ton {

        /**
         * Включен ли TON Wallet
         */
        private Boolean enabled = false;

        /**
         * API ключ TON Wallet
         */
        private String apiKey = "${TON_API_KEY:}";

        /**
         * Секретный ключ TON Wallet
         */
        private String secretKey = "${TON_SECRET_KEY:}";

        /**
         * URL для API TON
         */
        @NotBlank(message = "TON API URL не может быть пустым")
        private String apiUrl = "${TON_API_URL:https://wallet-api.ton.org}";

        /**
         * Webhook URL для TON
         */
        private String webhookUrl = "${TON_WEBHOOK_URL:}";

        /**
         * Адрес кошелька для получения платежей
         */
        private String walletAddress = "${TON_WALLET_ADDRESS:}";

        /**
         * Комиссия сети (в процентах)
         */
        @Min(value = 0, message = "Комиссия не может быть отрицательной")
        private Double networkFeePercent = 1.0;
    }

    @Data
    public static class YooKassa {

        /**
         * Включена ли YooKassa
         */
        private Boolean enabled = false;

        /**
         * Shop ID в YooKassa
         */
        private String shopId = "${YOOKASSA_SHOP_ID:}";

        /**
         * Секретный ключ YooKassa
         */
        private String secretKey = "${YOOKASSA_SECRET_KEY:}";

        /**
         * URL для API YooKassa
         */
        @NotBlank(message = "YooKassa API URL не может быть пустым")
        private String apiUrl = "${YOOKASSA_API_URL:https://api.yookassa.ru/v3}";

        /**
         * Webhook URL для YooKassa
         */
        private String webhookUrl = "${YOOKASSA_WEBHOOK_URL:}";

        /**
         * Поддерживаемые методы оплаты (только USD)
         */
        private String[] supportedMethods = { "bank_card" };

        /**
         * Поддерживаемые валюты (только USD)
         */
        private String[] supportedCurrencies = { "USD" };

        /**
         * Автоматическое подтверждение платежей
         */
        private Boolean autoCapture = true;

        /**
         * Таймаут запроса в секундах
         */
        @Positive(message = "Таймаут должен быть положительным")
        private Integer requestTimeoutSeconds = 30;
    }

    @Data
    public static class Fragment {

        /**
         * Включен ли Telegram Fragment API
         */
        private Boolean enabled = false;

        /**
         * Токен для Telegram Fragment API
         */
        private String token = "${FRAGMENT_API_TOKEN:}";

        /**
         * URL для Telegram Fragment API
         */
        @NotBlank(message = "Fragment API URL не может быть пустым")
        private String apiUrl = "${FRAGMENT_API_URL:https://fragment.com/api}";

        /**
         * Webhook URL для уведомлений от Fragment
         */
        private String webhookUrl = "${FRAGMENT_WEBHOOK_URL:}";

        /**
         * Поддерживаемые методы оплаты
         */
        private String[] supportedMethods = { "stars", "ton", "wallet" };

        /**
         * Валюты для Telegram Stars (XTR)
         */
        private String[] supportedCurrencies = { "XTR", "USD" };

        /**
         * Минимальная сумма для покупки Stars
         */
        @Positive(message = "Минимальная сумма должна быть положительной")
        private Integer minStarsAmount = 1;

        /**
         * Максимальная сумма для покупки Stars
         */
        @Positive(message = "Максимальная сумма должна быть положительной")
        private Integer maxStarsAmount = 2500;

        /**
         * Таймаут запроса в секундах
         */
        @Positive(message = "Таймаут должен быть положительным")
        private Integer requestTimeoutSeconds = 30;

        /**
         * Включить тестовый режим
         */
        private Boolean testMode = true;
    }

    @Data
    public static class UzsPayment {

        /**
         * Включена ли UZS платежная система
         */
        private Boolean enabled = false;

        /**
         * Merchant ID для UZS системы
         */
        private String merchantId = "${UZS_MERCHANT_ID:}";

        /**
         * Секретный ключ для UZS системы
         */
        private String secretKey = "${UZS_SECRET_KEY:}";

        /**
         * URL для API UZS платежей
         */
        @NotBlank(message = "UZS API URL не может быть пустым")
        private String apiUrl = "${UZS_API_URL:https://api.uzcard.uz}";

        /**
         * Webhook URL для UZS уведомлений
         */
        private String webhookUrl = "${UZS_WEBHOOK_URL:}";

        /**
         * Поддерживаемые методы оплаты для UZS
         */
        private String[] supportedMethods = { "uzcard", "humo", "visa", "mastercard" };

        /**
         * Валюта по умолчанию (UZS)
         */
        private String defaultCurrency = "UZS";

        /**
         * Минимальная сумма платежа в UZS
         */
        @Positive(message = "Минимальная сумма должна быть положительной")
        private Long minAmountUzs = 10000L;

        /**
         * Максимальная сумма платежа в UZS
         */
        @Positive(message = "Максимальная сумма должна быть положительной")
        private Long maxAmountUzs = 50000000L;

        /**
         * Комиссия системы (в процентах)
         */
        @Min(value = 0, message = "Комиссия не может быть отрицательной")
        private Double systemFeePercent = 2.0;

        /**
         * Таймаут запроса в секундах
         */
        @Positive(message = "Таймаут должен быть положительным")
        private Integer requestTimeoutSeconds = 60;

        /**
         * Включить тестовый режим
         */
        private Boolean testMode = true;
    }

}