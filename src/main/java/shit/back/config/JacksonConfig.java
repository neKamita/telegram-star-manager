package shit.back.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Глобальная конфигурация Jackson для корректной сериализации LocalDateTime и
 * других типов Java Time API.
 * Исправляет ошибку: "Could not write JSON: Java 8 date/time type
 * `java.time.LocalDateTime` not supported by default"
 */
@Configuration
public class JacksonConfig {

    /**
     * Создает и настраивает ObjectMapper с поддержкой Java Time API.
     * 
     * @return настроенный ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Регистрируем модуль для работы с Java Time API
        mapper.registerModule(new JavaTimeModule());

        // Отключаем сериализацию дат как timestamps (используем ISO-8601 формат)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Игнорируем неизвестные свойства при десериализации
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }
}