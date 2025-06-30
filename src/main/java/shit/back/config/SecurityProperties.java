package shit.back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

@Data
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private Api api = new Api();
    private RateLimit rateLimit = new RateLimit();
    private Validation validation = new Validation();
    private Audit audit = new Audit();
    private Cors cors = new Cors();

    @Data
    public static class Api {
        private String key = System.getenv("API_SECRET_KEY");
        private boolean enabled = true;
        private String headerName = "X-API-KEY";
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int userRequestsPerMinute = 10;
        private int apiRequestsPerMinute = 100;
        private long cleanupInterval = 60000; // 1 minute
    }

    @Data
    public static class Validation {
        private boolean enabled = true;
        private int maxMessageLength = 4096;
        private int maxCallbackDataLength = 64;
        private List<String> allowedCallbackPrefixes;

        // Инициализация значений по умолчанию
        public List<String> getAllowedCallbackPrefixes() {
            if (allowedCallbackPrefixes == null) {

                allowedCallbackPrefixes = new ArrayList<>();

                allowedCallbackPrefixes.add("buy_stars");
                allowedCallbackPrefixes.add("show_prices");
                allowedCallbackPrefixes.add("help");
                allowedCallbackPrefixes.add("my_orders");
                allowedCallbackPrefixes.add("back_to_main");
                allowedCallbackPrefixes.add("select_package_");
                allowedCallbackPrefixes.add("confirm_order_");
                allowedCallbackPrefixes.add("cancel_order");
                allowedCallbackPrefixes.add("pay_ton_");
                allowedCallbackPrefixes.add("pay_crypto_");
                allowedCallbackPrefixes.add("check_payment_");

                // === БАЛАНС CALLBACK'Ы ===
                allowedCallbackPrefixes.add("show_balance");
                allowedCallbackPrefixes.add("topup_balance_menu");
                allowedCallbackPrefixes.add("topup_balance_");
                allowedCallbackPrefixes.add("show_balance_history");
                allowedCallbackPrefixes.add("refresh_balance_history");
                allowedCallbackPrefixes.add("back_to_balance");
                allowedCallbackPrefixes.add("balance_payment_");
                allowedCallbackPrefixes.add("mixed_payment_");
                allowedCallbackPrefixes.add("export_balance_history");
                allowedCallbackPrefixes.add("topup_balance_custom");

                // === ПЛАТЕЖНЫЕ СИСТЕМЫ ===
                allowedCallbackPrefixes.add("topup_ton_");
                allowedCallbackPrefixes.add("topup_yookassa_");
                allowedCallbackPrefixes.add("topup_fragment_");
                allowedCallbackPrefixes.add("topup_uzs_");

                // === ТЕСТОВЫЕ CALLBACK'Ы ===
                allowedCallbackPrefixes.add("test_payment_");

            }
            return allowedCallbackPrefixes;
        }
    }

    @Data
    public static class Audit {
        private boolean enabled = true;
        private String logLevel = "INFO";
        private boolean logSensitiveData = false;
    }

    @Data
    public static class Cors {
        private boolean enabled = true;
        private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:8080");
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE");
        private List<String> allowedHeaders = List.of("Content-Type", "Authorization", "X-API-KEY");
        private long maxAge = 3600;
    }
}
