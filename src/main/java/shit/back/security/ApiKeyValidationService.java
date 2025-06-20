package shit.back.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shit.back.config.SecurityProperties;

/**
 * Сервис для валидации API-ключа.
 * Отвечает только за проверку валидности ключа.
 */
@Service
public class ApiKeyValidationService {

    private final SecurityProperties securityProperties;

    @Autowired
    public ApiKeyValidationService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * Проверяет валидность API-ключа.
     * 
     * @param apiKey ключ из запроса
     * @return true если ключ валиден
     */
    public boolean isValidApiKey(String apiKey) {
        // Здесь может быть расширяемая логика (например, проверка в БД или по JWT)
        return securityProperties.getApi().getKey().equals(apiKey);
    }
}