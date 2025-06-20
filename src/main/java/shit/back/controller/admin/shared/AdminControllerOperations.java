package shit.back.controller.admin.shared;

import java.util.Map;

/**
 * Унифицированный базовый интерфейс для всех админских контроллеров
 * Определяет общие операции для UI и API контроллеров
 * Следует принципам SOLID - Interface Segregation Principle
 */
public interface AdminControllerOperations {

    /**
     * Создание стандартного ответа об ошибке
     * 
     * @param message сообщение об ошибке
     * @param e       исключение (может быть null)
     * @return Map с информацией об ошибке
     */
    Map<String, Object> createErrorResponse(String message, Exception e);

    /**
     * Создание успешного ответа
     * 
     * @param message сообщение об успехе
     * @param data    данные (может быть null)
     * @return Map с информацией об успехе
     */
    default Map<String, Object> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("message", message);
        if (data != null) {
            response.put("data", data);
        }
        response.put("timestamp", java.time.LocalDateTime.now());
        return response;
    }
}