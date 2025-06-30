package shit.back.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import shit.back.telegram.TelegramService;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.queries.ShowBalanceQuery;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тест для проверки исправления ошибки "Keyboard parameter can't be null"
 */
@ExtendWith(MockitoExtension.class)
class TelegramHandlerFacadeKeyboardTest {

    @Mock
    private TelegramService telegramService;

    @InjectMocks
    private TelegramHandlerFacade telegramHandlerFacade;

    private Message mockMessage;
    private CallbackQuery mockCallbackQuery;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(123456L);
        mockUser.setFirstName("TestUser");
        mockUser.setUserName("testuser");

        mockMessage = new Message();
        mockMessage.setMessageId(1);
        mockMessage.setText("/balance");
        mockMessage.setFrom(mockUser);

        // Устанавливаем chat через reflection или mock
        org.telegram.telegrambots.meta.api.objects.Chat chat = new org.telegram.telegrambots.meta.api.objects.Chat();
        chat.setId(123456L);
        mockMessage.setChat(chat);

        mockCallbackQuery = new CallbackQuery();
        mockCallbackQuery.setId("callback123");
        mockCallbackQuery.setData("refresh_balance");
        mockCallbackQuery.setFrom(mockUser);

        Message callbackMessage = new Message();
        callbackMessage.setMessageId(2);
        callbackMessage.setChat(chat);
        mockCallbackQuery.setMessage(callbackMessage);
    }

    @Test
    void testProcessMessage_WithValidKeyboard_ShouldIncludeKeyboard() {
        // Arrange: Создаем response с корректной клавиатурой
        InlineKeyboardMarkup keyboard = new TelegramKeyboardBuilder()
                .addButton("⭐ Купить звезды", "buy_stars")
                .addButton("🔄 Обновить", "refresh_balance")
                .build();

        TelegramResponse response = TelegramResponse.builder()
                .successful(true)
                .message("Ваш баланс: 100 RUB")
                .data(keyboard)
                .build();

        when(telegramService.execute(any(ShowBalanceQuery.class))).thenReturn(response);

        // Act
        var result = telegramHandlerFacade.processMessage(mockMessage);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        SendMessage sendMessage = (SendMessage) result;
        assertNotNull(sendMessage.getReplyMarkup());
        assertEquals("Ваш баланс: 100 RUB", sendMessage.getText());
    }

    @Test
    void testProcessMessage_WithNullKeyboard_ShouldNotIncludeKeyboard() {
        // Arrange: Создаем response без клавиатуры
        TelegramResponse response = TelegramResponse.builder()
                .successful(true)
                .message("Ваш баланс: 100 RUB")
                .data(null)
                .build();

        when(telegramService.execute(any(ShowBalanceQuery.class))).thenReturn(response);

        // Act
        var result = telegramHandlerFacade.processMessage(mockMessage);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        SendMessage sendMessage = (SendMessage) result;
        assertNull(sendMessage.getReplyMarkup());
        assertEquals("Ваш баланс: 100 RUB", sendMessage.getText());
    }

    @Test
    void testProcessMessage_WithEmptyKeyboard_ShouldNotIncludeKeyboard() {
        // Arrange: Создаем response с пустой клавиатурой
        InlineKeyboardMarkup emptyKeyboard = new InlineKeyboardMarkup();
        emptyKeyboard.setKeyboard(List.of()); // Пустой список кнопок

        TelegramResponse response = TelegramResponse.builder()
                .successful(true)
                .message("Ваш баланс: 100 RUB")
                .data(emptyKeyboard)
                .build();

        when(telegramService.execute(any(ShowBalanceQuery.class))).thenReturn(response);

        // Act
        var result = telegramHandlerFacade.processMessage(mockMessage);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        SendMessage sendMessage = (SendMessage) result;
        assertNull(sendMessage.getReplyMarkup());
        assertEquals("Ваш баланс: 100 RUB", sendMessage.getText());
    }

    @Test
    void testProcessCallbackQuery_WithValidKeyboard_ShouldIncludeKeyboard() {
        // Arrange: Создаем response с корректной клавиатурой
        InlineKeyboardMarkup keyboard = new TelegramKeyboardBuilder()
                .addButton("💰 Баланс", "show_balance")
                .addButton("🔄 Обновить", "refresh_balance")
                .build();

        TelegramResponse response = TelegramResponse.builder()
                .successful(true)
                .message("Баланс обновлен: 150 RUB")
                .data(keyboard)
                .build();

        when(telegramService.execute(any(ShowBalanceQuery.class))).thenReturn(response);

        // Act
        var result = telegramHandlerFacade.processCallbackQuery(mockCallbackQuery);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof EditMessageText);
        EditMessageText editMessage = (EditMessageText) result;
        assertNotNull(editMessage.getReplyMarkup());
        assertEquals("Баланс обновлен: 150 RUB", editMessage.getText());
    }

    @Test
    void testProcessCallbackQuery_WithNullKeyboard_ShouldNotIncludeKeyboard() {
        // Arrange: Создаем response без клавиатуры
        TelegramResponse response = TelegramResponse.builder()
                .successful(true)
                .message("Баланс обновлен: 150 RUB")
                .data(null)
                .build();

        when(telegramService.execute(any(ShowBalanceQuery.class))).thenReturn(response);

        // Act
        var result = telegramHandlerFacade.processCallbackQuery(mockCallbackQuery);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof EditMessageText);
        EditMessageText editMessage = (EditMessageText) result;
        assertNull(editMessage.getReplyMarkup());
        assertEquals("Баланс обновлен: 150 RUB", editMessage.getText());
    }
}