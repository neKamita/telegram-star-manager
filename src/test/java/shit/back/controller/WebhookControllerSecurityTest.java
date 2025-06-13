package shit.back.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import shit.back.service.TelegramWebhookBotService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тест безопасности для WebhookController
 * Проверяет, что персональные данные не попадают в логи
 */
@ExtendWith(MockitoExtension.class)
class WebhookControllerSecurityTest {

    @Mock
    private TelegramWebhookBotService telegramWebhookBotService;

    @Mock
    private Logger mockLogger;

    @InjectMocks
    private WebhookController webhookController;

    private Update testUpdate;
    private User testUser;
    private Message testMessage;
    private CallbackQuery testCallbackQuery;

    @BeforeEach
    void setUp() {
        // Заменяем реальный логгер на мок для проверки
        ReflectionTestUtils.setField(webhookController, "logger", mockLogger);

        // Создаем тестовые данные с реальными персональными данными
        testUser = new User();
        testUser.setId(123456789L);
        testUser.setFirstName("Иван");
        testUser.setLastName("Петров");
        testUser.setUserName("ivan_petrov");

        testMessage = new Message();
        testMessage.setFrom(testUser);
        testMessage.setText("Привет! Это секретное сообщение с персональными данными.");

        testCallbackQuery = new CallbackQuery();
        testCallbackQuery.setFrom(testUser);
        testCallbackQuery.setData("sensitive_callback_data");

        testUpdate = new Update();
        testUpdate.setUpdateId(12345);
    }

    @Test
    void testMessageLoggingDoesNotContainPersonalData() {
        // Arrange
        testUpdate.setMessage(testMessage);
        when(telegramWebhookBotService.onWebhookUpdateReceived(any())).thenReturn(null);

        // Act
        ResponseEntity<?> response = webhookController.webhook(testUpdate);

        // Assert
        assertNotNull(response);

        // Проверяем, что логи НЕ содержат персональные данные
        verify(mockLogger, never()).info(contains("Иван"));
        verify(mockLogger, never()).info(contains("Петров"));
        verify(mockLogger, never()).info(contains("ivan_petrov"));
        verify(mockLogger, never()).info(contains("123456789"));
        verify(mockLogger, never()).info(contains("секретное сообщение"));
        verify(mockLogger, never()).info(contains("персональными данными"));

        // Проверяем, что логи содержат только безопасную информацию
        verify(mockLogger).info(matches(".*\\[АНОНИМНО\\].*"));
        verify(mockLogger).info(matches(".*HASH_[A-F0-9]{8}.*"));
        verify(mockLogger).info(matches(".*тип=TEXT.*"));
    }

    @Test
    void testCallbackQueryLoggingDoesNotContainPersonalData() {
        // Arrange
        testUpdate.setCallbackQuery(testCallbackQuery);
        when(telegramWebhookBotService.onWebhookUpdateReceived(any())).thenReturn(null);

        // Act
        ResponseEntity<?> response = webhookController.webhook(testUpdate);

        // Assert
        assertNotNull(response);

        // Проверяем, что логи НЕ содержат персональные данные
        verify(mockLogger, never()).info(contains("Иван"));
        verify(mockLogger, never()).info(contains("Петров"));
        verify(mockLogger, never()).info(contains("ivan_petrov"));
        verify(mockLogger, never()).info(contains("123456789"));
        verify(mockLogger, never()).info(contains("sensitive_callback_data"));

        // Проверяем, что логи содержат только безопасную информацию
        verify(mockLogger).info(matches(".*\\[АНОНИМНО\\].*"));
        verify(mockLogger).info(matches(".*HASH_[A-F0-9]{8}.*"));
        verify(mockLogger).info(matches(".*тип=DATA_CALLBACK.*"));
    }

    @Test
    void testMaskUserDataMethodProducesConsistentHashes() {
        // Arrange
        Long userId1 = 123456789L;
        Long userId2 = 987654321L;
        Long userId3 = 123456789L; // Тот же ID что и userId1

        // Act
        String hash1 = invokePrivateMaskUserData(userId1);
        String hash2 = invokePrivateMaskUserData(userId2);
        String hash3 = invokePrivateMaskUserData(userId3);

        // Assert
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotNull(hash3);

        // Хеши должны быть разными для разных ID
        assertNotEquals(hash1, hash2);

        // Хеши должны быть одинаковыми для одного ID
        assertEquals(hash1, hash3);

        // Проверяем формат хеша
        assertTrue(hash1.matches("HASH_[A-F0-9]{8}"));
        assertTrue(hash2.matches("HASH_[A-F0-9]{8}"));

        // Хеш не должен содержать оригинальный ID
        assertFalse(hash1.contains("123456789"));
        assertFalse(hash2.contains("987654321"));
    }

    @Test
    void testMaskUserDataHandlesNullInput() {
        // Act
        String result = invokePrivateMaskUserData(null);

        // Assert
        assertEquals("[НЕИЗВЕСТНО]", result);
    }

    @Test
    void testGDPRComplianceValidation() {
        // Arrange
        testUpdate.setMessage(testMessage);
        when(telegramWebhookBotService.onWebhookUpdateReceived(any())).thenReturn(null);

        // Act
        webhookController.webhook(testUpdate);

        // Assert - Проверяем соответствие GDPR требованиям
        verify(mockLogger, never()).info(argThat(logMessage -> logMessage.toString().contains("Иван") ||
                logMessage.toString().contains("Петров") ||
                logMessage.toString().contains("ivan_petrov") ||
                logMessage.toString().contains("123456789") ||
                logMessage.toString().contains("секретное") ||
                logMessage.toString().contains("персональными")));

        // Проверяем, что логируется только техническая информация
        verify(mockLogger, atLeastOnce()).info(argThat(logMessage -> logMessage.toString().contains("[АНОНИМНО]") &&
                logMessage.toString().contains("HASH_") &&
                (logMessage.toString().contains("тип=") || logMessage.toString().contains("ID:"))));
    }

    /**
     * Вспомогательный метод для вызова приватного метода maskUserData
     */
    private String invokePrivateMaskUserData(Long userId) {
        try {
            return (String) ReflectionTestUtils.invokeMethod(webhookController, "maskUserData", userId);
        } catch (Exception e) {
            fail("Ошибка при вызове приватного метода maskUserData: " + e.getMessage());
            return null;
        }
    }
}