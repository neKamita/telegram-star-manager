package shit.back.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Основная конфигурация приложения
 * 
 * Централизованная регистрация всех ConfigurationProperties
 */
@Configuration
@EnableConfigurationProperties({
        PaymentConfigurationProperties.class,
        SecurityProperties.class,
        SystemConfigurationProperties.class,
        MetricsConfigurationProperties.class
})
public class ApplicationConfiguration {
    // Класс служит только для регистрации ConfigurationProperties
}