package shit.back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    
    private Api api = new Api();
    private RateLimit rateLimit = new RateLimit();
    private Validation validation = new Validation();
    private Audit audit = new Audit();
    private Cors cors = new Cors();
    
    @Data
    public static class Api {
        private String key = "default-key-change-me";
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
        private List<String> allowedCallbackPrefixes = new ArrayList<>();
        
        // Инициализация значений по умолчанию
        public List<String> getAllowedCallbackPrefixes() {
            if (allowedCallbackPrefixes.isEmpty()) {
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
