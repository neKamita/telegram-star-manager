package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Диагностический контроллер для проверки доступности эндпоинтов
 * ВРЕМЕННЫЙ - для диагностики проблемы с NoResourceFoundException
 */
@Slf4j
@RestController
@RequestMapping("/diagnostic")
public class DiagnosticEndpointsController {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * Диагностический эндпоинт для проверки всех зарегистрированных маршрутов
     */
    @GetMapping("/endpoints")
    public ResponseEntity<Map<String, Object>> getAllEndpoints(HttpServletRequest request) {
        log.error("🚨 ДИАГНОСТИКА: Запрос диагностики всех endpoints");

        Map<String, Object> result = new HashMap<>();

        try {
            // Проверяем все зарегистрированные маршруты
            Map<String, String> allMappings = requestMappingHandlerMapping
                    .getHandlerMethods()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            entry -> entry.getValue().getMethod().getDeclaringClass().getSimpleName()
                                    + "." + entry.getValue().getMethod().getName()));

            result.put("totalEndpoints", allMappings.size());
            result.put("allMappings", allMappings);

            // Ищем админские эндпоинты
            Map<String, String> adminEndpoints = allMappings.entrySet().stream()
                    .filter(entry -> entry.getKey().contains("/admin/"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            result.put("adminEndpoints", adminEndpoints);
            result.put("adminEndpointsCount", adminEndpoints.size());

            // Ищем конкретные проблемные эндпоинты
            boolean hasDashboardData = allMappings.keySet().stream()
                    .anyMatch(key -> key.contains("dashboard-data"));
            boolean hasSystemHealth = allMappings.keySet().stream()
                    .anyMatch(key -> key.contains("system-health"));

            result.put("hasDashboardDataEndpoint", hasDashboardData);
            result.put("hasSystemHealthEndpoint", hasSystemHealth);

            // Проверяем контроллеры
            String[] controllerBeans = applicationContext.getBeanNamesForType(Object.class);
            long adminControllerCount = java.util.Arrays.stream(controllerBeans)
                    .filter(name -> name.toLowerCase().contains("admin") && name.toLowerCase().contains("controller"))
                    .count();

            result.put("adminControllerBeans", adminControllerCount);
            result.put("timestamp", LocalDateTime.now());
            result.put("success", true);

            log.error("🚨 ДИАГНОСТИКА РЕЗУЛЬТАТ: Admin endpoints найдено {}, dashboard-data: {}, system-health: {}",
                    adminEndpoints.size(), hasDashboardData, hasSystemHealth);

        } catch (Exception e) {
            log.error("🚨 ДИАГНОСТИКА ОШИБКА: Не удалось получить список endpoints", e);
            result.put("error", e.getMessage());
            result.put("success", false);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Тестовый эндпоинт для проверки базовой работоспособности
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        log.error("🚨 ДИАГНОСТИКА: Тестовый endpoint вызван успешно!");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Диагностический контроллер работает");
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }
}