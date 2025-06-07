package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import shit.back.model.FeatureFlag;
import shit.back.service.ConfigurationRefreshService;
import shit.back.service.FeatureFlagService;
import shit.back.utils.FallbackUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin")
public class AdminController {
    
    @Autowired
    private FeatureFlagService featureFlagService;
    
    @Autowired
    private ConfigurationRefreshService configurationRefreshService;
    
    @GetMapping
    public String adminDashboard(Model model) {
        try {
            FallbackUtils.AdminData data = new FallbackUtils.AdminData(featureFlagService);
            
            model.addAttribute("title", "Dashboard");
            model.addAttribute("subtitle", "Feature Flags Overview");
            model.addAttribute("flags", data.allFlags);
            model.addAttribute("activeFlags", data.activeFlags);
            model.addAttribute("totalFlags", data.totalFlags);
            model.addAttribute("activeFlagsCount", data.activeFlagsCount);
            model.addAttribute("cacheSize", data.cacheSize);
            
            return "admin/dashboard";
        } catch (Exception e) {
            log.error("Error loading admin dashboard: {}", e.getMessage());
            model.addAttribute("error", "Ошибка загрузки панели администратора.");
            return "admin/error";
        }
    }
    
    @GetMapping("/feature-flags")
    public String featureFlagsPage(Model model) {
        try {
            List<FeatureFlag> flags = FallbackUtils.getAllFlagsWithFallback(featureFlagService);
            
            model.addAttribute("title", "Feature Flags");
            model.addAttribute("subtitle", "Manage all feature flags");
            model.addAttribute("flags", flags);
            return "admin/feature-flags";
        } catch (Exception e) {
            log.error("Error loading feature flags page: {}", e.getMessage());
            model.addAttribute("error", "Ошибка загрузки флагов функций.");
            return "admin/error";
        }
    }
    
    @GetMapping("/monitoring")
    public String monitoringPage(Model model) {
        try {
            FallbackUtils.AdminData data = new FallbackUtils.AdminData(featureFlagService);
            
            model.addAttribute("title", "Monitoring");
            model.addAttribute("subtitle", "System Health & Performance Monitoring");
            model.addAttribute("totalFlags", data.totalFlags);
            model.addAttribute("activeFlags", data.activeFlagsCount);
            model.addAttribute("cacheSize", data.cacheSize);
            
            return "admin/monitoring";
        } catch (Exception e) {
            log.error("Error loading monitoring page: {}", e.getMessage());
            model.addAttribute("error", "Ошибка загрузки страницы мониторинга.");
            return "admin/error";
        }
    }
    
    @GetMapping("/feature-flags/new")
    public String newFeatureFlagPage(Model model) {
        model.addAttribute("flag", new FeatureFlag());
        model.addAttribute("isEdit", false);
        return "admin/feature-flag-form";
    }
    
    @GetMapping("/feature-flags/{flagName}/edit")
    public String editFeatureFlagPage(@PathVariable String flagName, Model model) {
        try {
            FeatureFlag flag = featureFlagService.getFeatureFlag(flagName)
                    .orElseThrow(() -> new RuntimeException("Feature flag not found"));
            model.addAttribute("flag", flag);
            model.addAttribute("isEdit", true);
            return "admin/feature-flag-form";
        } catch (Exception e) {
            log.error("Error loading feature flag '{}' for editing: {}", flagName, e.getMessage());
            model.addAttribute("error", "Флаг функции не найден");
            return "admin/error";
        }
    }
    
    @PostMapping("/feature-flags")
    public String saveFeatureFlag(@ModelAttribute FeatureFlag flag) {
        try {
            if (flag.getName() == null || flag.getName().trim().isEmpty()) {
                throw new RuntimeException("Flag name is required");
            }
            
            // Устанавливаем значения по умолчанию
            if (flag.getCreatedBy() == null) {
                flag.setCreatedBy("Admin");
            }
            if (flag.getEnvironment() == null) {
                flag.setEnvironment("production");
            }
            if (flag.getVersion() == null) {
                flag.setVersion("1.0");
            }
            
            featureFlagService.createFeatureFlag(flag);
            log.info("Feature flag '{}' created via admin panel", flag.getName());
            
            return "redirect:/admin/feature-flags?success=created";
        } catch (Exception e) {
            log.error("Error creating feature flag: {}", e.getMessage());
            return "redirect:/admin/feature-flags?error=" + e.getMessage();
        }
    }
    
    @PostMapping("/feature-flags/{flagName}/toggle")
    @ResponseBody
    public String toggleFeatureFlag(@PathVariable String flagName) {
        try {
            featureFlagService.toggleFeatureFlag(flagName, "Admin");
            
            // Применяем изменения немедленно
            configurationRefreshService.applyFeatureFlagChange(flagName);
            
            boolean isEnabled = featureFlagService.getFeatureFlag(flagName)
                    .map(FeatureFlag::isEnabled)
                    .orElse(false);
            
            return isEnabled ? "enabled" : "disabled";
        } catch (Exception e) {
            log.error("Error toggling feature flag '{}': {}", flagName, e.getMessage());
            return "error";
        }
    }
    
    @PostMapping("/feature-flags/{flagName}/delete")
    public String deleteFeatureFlag(@PathVariable String flagName) {
        try {
            featureFlagService.deleteFeatureFlag(flagName);
            log.info("Feature flag '{}' deleted via admin panel", flagName);
            return "redirect:/admin/feature-flags?success=deleted";
        } catch (Exception e) {
            log.error("Error deleting feature flag '{}': {}", flagName, e.getMessage());
            return "redirect:/admin/feature-flags?error=" + e.getMessage();
        }
    }
    
    @PostMapping("/refresh-cache")
    public String refreshCache() {
        try {
            configurationRefreshService.refreshConfiguration();
            log.info("Configuration cache refreshed via admin panel");
            return "redirect:/admin?success=cache_refreshed";
        } catch (Exception e) {
            log.error("Error refreshing cache: {}", e.getMessage());
            return "redirect:/admin?error=" + e.getMessage();
        }
    }
    
    @GetMapping(value = "/api/dashboard-data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public DashboardData getDashboardData() {
        FallbackUtils.AdminData data = new FallbackUtils.AdminData(featureFlagService);
        
        return DashboardData.builder()
                .totalFlags(data.totalFlags)
                .activeFlags(data.activeFlagsCount)
                .cacheSize(data.cacheSize)
                .lastUpdated(LocalDateTime.now())
                .flags(data.allFlags)
                .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DashboardData {
        private int totalFlags;
        private int activeFlags;
        private int cacheSize;
        private LocalDateTime lastUpdated;
        private List<FeatureFlag> flags;
    }
}
