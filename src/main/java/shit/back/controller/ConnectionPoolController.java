package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shit.back.service.ConnectionPoolMonitoringService;

import java.util.Map;

/**
 * REST API для мониторинга connection pools
 */
@Slf4j
@RestController
@RequestMapping("/api/monitoring")
public class ConnectionPoolController {

    @Autowired
    private ConnectionPoolMonitoringService connectionPoolMonitoringService;

    /**
     * Получение статистики connection pools
     */
    @GetMapping("/connection-pools")
    public ResponseEntity<Map<String, Object>> getConnectionPoolStats() {
        try {
            Map<String, Object> stats = connectionPoolMonitoringService.getConnectionPoolStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("❌ Ошибка при получении статистики connection pools: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Проверка health состояния connection pools
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        try {
            var health = connectionPoolMonitoringService.health();
            
            Map<String, Object> response = Map.of(
                "status", health.getStatus().getCode(),
                "details", health.getDetails()
            );
            
            if ("UP".equals(health.getStatus().getCode())) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(503).body(response);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке health: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
