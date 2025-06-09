package shit.back.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Конфигурация HTTP клиента с connection pooling
 * Оптимизировано для ограниченных ресурсов Koyeb (512MB RAM, 0.1 vCPU)
 */
@Configuration
public class HttpClientConfig {

    /**
     * Создает оптимизированный RestTemplate с connection pooling
     * для минимизации использования ресурсов
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        // Настройка connection pool manager с ограниченными ресурсами
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        
        // Ограничиваем общее количество соединений для экономии памяти
        connectionManager.setMaxTotal(5); // Максимум 5 соединений всего
        connectionManager.setDefaultMaxPerRoute(2); // Максимум 2 соединения на маршрут
        
        // Настройка тайм-аутов для освобождения ресурсов
        connectionManager.setValidateAfterInactivity(Timeout.of(10, TimeUnit.SECONDS));
        
        // Настройка конфигурации запросов
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(5, TimeUnit.SECONDS)) // Тайм-аут получения соединения из pool
                .setConnectTimeout(Timeout.of(10, TimeUnit.SECONDS)) // Тайм-аут подключения
                .setResponseTimeout(Timeout.of(30, TimeUnit.SECONDS)) // Тайм-аут ответа
                .build();

        // Создание HTTP клиента с оптимизациями
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections() // Автоматическое удаление истекших соединений
                .evictIdleConnections(Timeout.of(30, TimeUnit.SECONDS)) // Удаление idle соединений через 30 сек
                .build();

        // Создание factory с оптимизированным HTTP клиентом
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        
        return new RestTemplate(factory);
    }

    /**
     * Специальный RestTemplate для самотестирования бота
     * с более агрессивными тайм-аутами для быстрых проверок
     */
    @Bean("selfTestRestTemplate")
    public RestTemplate selfTestRestTemplate() {
        // Более ограниченный connection pool для self-test
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(2); // Максимум 2 соединения
        connectionManager.setDefaultMaxPerRoute(1); // 1 соединение на маршрут
        connectionManager.setValidateAfterInactivity(Timeout.of(5, TimeUnit.SECONDS));

        // Более короткие тайм-ауты для быстрых проверок
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(2, TimeUnit.SECONDS))
                .setConnectTimeout(Timeout.of(5, TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(15, TimeUnit.SECONDS))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(Timeout.of(15, TimeUnit.SECONDS))
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        
        return new RestTemplate(factory);
    }
}
