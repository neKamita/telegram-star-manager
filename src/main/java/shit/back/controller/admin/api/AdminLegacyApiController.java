package shit.back.controller.admin.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;

/**
 * Legacy API контроллер для админ панели
 * Обеспечивает обратную совместимость со старыми API endpoints
 * Делегирует вызовы основному AdminDashboardApiController
 */
@RestController
public class AdminLegacyApiController {

    private static final Logger log = LoggerFactory.getLogger(AdminLegacyApiController.class);

    private final AdminDashboardApiController dashboardController;

    @Autowired
    public AdminLegacyApiController(AdminDashboardApiController dashboardController) {
        this.dashboardController = dashboardController;
    }

    /**
     * Legacy endpoint для получения данных дашборда
     * Путь: /admin/api/dashboard-data
     * Делегирует к AdminDashboardApiController.getDashboardOverview()
     */
    @GetMapping("/admin/api/dashboard-data")
    public ResponseEntity<Object> getDashboardDataLegacy(HttpServletRequest request) {
        log.debug("Legacy API: dashboard-data endpoint called, delegating to dashboard controller");
        return dashboardController.getDashboardOverview(request);
    }

    /**
     * Legacy endpoint для получения здоровья системы
     * Путь: /admin/api/system-health
     * Делегирует к AdminDashboardApiController.getSystemHealth()
     */
    @GetMapping("/admin/api/system-health")
    public CompletableFuture<ResponseEntity<Object>> getSystemHealthLegacy(HttpServletRequest request) {
        log.debug("Legacy API: system-health endpoint called, delegating to dashboard controller");
        log.info("🔧 ИСПРАВЛЕНИЕ: System health request from: {}, User-Agent: {}",
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return dashboardController.getSystemHealth(request);
    }
}