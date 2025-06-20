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
@Component
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
     * Настройки Qiwi
     */
    @NotNull
    private Qiwi qiwi = new Qiwi();

    /**
     * Настройки SberPay
     */
    @NotNull
    private SberPay sberpay = new SberPay();

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
         * Поддерживаемые методы оплаты
         */
        private String[] supportedMethods = { "bank_card", "yoo_money", "sberbank", "qiwi" };

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
    public static class Qiwi {

        /**
         * Включен ли Qiwi
         */
        private Boolean enabled = false;

        /**
         * Публичный ключ Qiwi
         */
        private String publicKey = "${QIWI_PUBLIC_KEY:}";

        /**
         * Секретный ключ Qiwi
         */
        private String secretKey = "${QIWI_SECRET_KEY:}";

        /**
         * URL для API Qiwi
         */
        @NotBlank(message = "Qiwi API URL не может быть пустым")
        private String apiUrl = "${QIWI_API_URL:https://api.qiwi.com}";

        /**
         * Webhook URL для Qiwi
         */
        private String webhookUrl = "${QIWI_WEBHOOK_URL:}";

        /**
         * ID сайта в системе Qiwi
         */
        private String siteId = "${QIWI_SITE_ID:}";

        /**
         * Валюта по умолчанию
         */
        private String defaultCurrency = "RUB";

        /**
         * Время жизни счета в минутах
         */
        @Positive(message = "Время жизни должно быть положительным")
        private Integer billLifetimeMinutes = 60;
    }

    @Data
    public static class SberPay {

        /**
         * Включен ли SberPay
         */
        private Boolean enabled = false;

        /**
         * Merchant ID в SberPay
         */
        private String merchantId = "${SBERPAY_MERCHANT_ID:}";

        /**
         * Секретный ключ SberPay
         */
        private String secretKey = "${SBERPAY_SECRET_KEY:}";

        /**
         * URL для API SberPay
         */
        @NotBlank(message = "SberPay API URL не может быть пустым")
        private String apiUrl = "${SBERPAY_API_URL:https://securepayments.sberbank.ru}";

        /**
         * Webhook URL для SberPay
         */
        private String webhookUrl = "${SBERPAY_WEBHOOK_URL:}";

        /**
         * Логин для доступа к API
         */
        private String apiLogin = "${SBERPAY_API_LOGIN:}";

        /**
         * Пароль для доступа к API
         */
        private String apiPassword = "${SBERPAY_API_PASSWORD:}";

        /**
         * Режим тестирования
         */
        private Boolean testMode = true;

        /**
         * Валюта по умолчанию (643 = RUB)
         */
        private String defaultCurrencyCode = "643";
    }

}