package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shit.back.service.TelegramBotService;
import shit.back.service.UserSessionService;
import shit.back.service.PriceService;
import shit.back.model.StarPackage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bot")
public class BotController {
    
    @Autowired
    private TelegramBotService telegramBotService;
    
    @Autowired
    private UserSessionService userSessionService;
    
    @Autowired
    private PriceService priceService;
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBotStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("botUsername", telegramBotService.getBotUsername());
        status.put("activeSessions", userSessionService.getActiveSessionsCount());
        status.put("totalOrders", userSessionService.getTotalOrdersCount());
        status.put("status", "running");
        
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/prices")
    public ResponseEntity<List<StarPackage>> getPrices() {
        return ResponseEntity.ok(priceService.getAllPackages());
    }
    
    @PostMapping("/send-message")
    public ResponseEntity<Map<String, String>> sendMessage(
            @RequestParam Long chatId,
            @RequestParam String message) {
        
        Map<String, String> response = new HashMap<>();
        
        // Check if bot is registered before attempting to send message
        if (!telegramBotService.isBotRegistered()) {
            log.warn("Attempt to send message while bot is not registered");
            response.put("status", "error");
            response.put("message", "Bot is not registered. Current status: " + telegramBotService.getBotStatus());
            response.put("botStatus", telegramBotService.getBotStatus());
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            telegramBotService.sendMessage(chatId, message);
            response.put("status", "success");
            response.put("message", "Message sent successfully");
            log.info("Message sent successfully to chatId: {}", chatId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending message to chatId {}: {}", chatId, e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/cleanup-sessions")
    public ResponseEntity<Map<String, String>> cleanupSessions() {
        try {
            userSessionService.cleanupOldSessions();
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Old sessions cleaned up successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "TelegramStarManager");
        return ResponseEntity.ok(health);
    }
}
