package shit.back.telegram.commands.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.service.BalanceApplicationFacade;
import shit.back.service.UserSessionUnifiedService;
import shit.back.telegram.commands.TelegramCommandHandler;
import shit.back.telegram.commands.TopupBalanceCommand;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Обработчик команды пополнения баланса
 * 
 * Интегрируется с существующими сервисами баланса
 */
@Component
@Slf4j
public class TopupBalanceCommandHandler implements TelegramCommandHandler<TopupBalanceCommand> {

    static {
        System.err.println("🔍 ДИАГНОСТИКА TM: TopupBalanceCommandHandler класс загружен");
    }

    @Autowired
    private BalanceApplicationFacade balanceApplicationFacade;

    @Autowired
    private UserSessionUnifiedService sessionService;

    // ИСПРАВЛЕНИЕ: Кэширование состояний для предотвращения множественных обращений
    // к БД
    private final ConcurrentMap<Long, CachedUserState> stateCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30000; // 30 секунд

    @Override
    @Transactional
    public TelegramResponse handle(TopupBalanceCommand command) throws Exception {
        log.info("💳 Обработка пополнения баланса: userId={}, amount={}, method={}",
                command.getUserId(), command.getAmount(), command.getPaymentMethod());

        try {
            // Валидация команды
            command.validate();

            if (!command.hasAmount()) {
                // Команда "начать пополнение" - переводим в состояние ввода суммы
                return handleTopupStart(command);
            } else {
                // Команда с суммой - обрабатываем пополнение
                return handleTopupWithAmount(command);
            }

        } catch (IllegalArgumentException e) {
            log.warn("❌ Некорректные данные для пополнения от пользователя {}: {}",
                    command.getUserId(), e.getMessage());
            return TelegramResponse.error("❌ " + e.getMessage());

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке пополнения баланса для пользователя {}: {}",
                    command.getUserId(), e.getMessage(), e);
            return TelegramResponse.error("Не удалось обработать запрос на пополнение: " + e.getMessage());
        }
    }

    @Override
    public Class<TopupBalanceCommand> getCommandType() {
        return TopupBalanceCommand.class;
    }

    @Override
    public int getHandlerPriority() {
        return 30; // Высокий приоритет для операций с балансом
    }

    @Override
    public String getDescription() {
        return "Обработчик команд пополнения баланса с поддержкой различных методов платежа";
    }

    /**
     * Обработка команды "начать пополнение"
     */
    private TelegramResponse handleTopupStart(TopupBalanceCommand command) {
        try {
            log.info("🔍 Обработка начала пополнения для пользователя: {}, способ: {}",
                    command.getUserId(), command.getPaymentMethod());

            if (command.getPaymentMethod() == null || "DEFAULT".equals(command.getPaymentMethod())) {
                // ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #2: Показываем способы оплаты СНАЧАЛА
                return showPaymentMethods(command.getUserId());
            } else {
                // Способ оплаты уже выбран, показываем выбор суммы
                return showAmountSelection(command);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке начала пополнения для пользователя {}: {}",
                    command.getUserId(), e.getMessage());
            return TelegramResponse.error("Не удалось инициировать процесс пополнения");
        }
    }

    /**
     * ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #1 и #2: Показ способов оплаты с user-friendly текстами
     */
    private TelegramResponse showPaymentMethods(Long userId) {
        log.info("💳 Показываем способы оплаты для пользователя: {}", userId);

        // Обновляем состояние сессии
        sessionService.updateSessionState(userId,
                shit.back.model.UserSession.SessionState.SELECTING_PAYMENT_TYPE);

        String message = """
                💰 <b>Пополнение баланса</b>

                💳 Выберите удобный способ оплаты:
                """;

        // Создаем клавиатуру с user-friendly способами оплаты
        var keyboard = new TelegramKeyboardBuilder()
                .addButton("₿ Криптовалюта", "payment_crypto")
                .newRow()
                .addButton("💳 YooMoney", "payment_yoomoney")
                .newRow()
                .addButton("💳 UZS карта", "payment_uzs")
                .newRow()
                .addButton(" Назад", "show_balance")
                .build();

        return TelegramResponse.builder()
                .successful(true)
                .message(message)
                .uiType("PAYMENT_METHOD_SELECTION")
                .data(keyboard)
                .build();
    }

    /**
     * ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #1: Показ выбора суммы с сохранением способа оплаты
     */
    private TelegramResponse showAmountSelection(TopupBalanceCommand command) {
        log.info("💰 Показываем выбор суммы для пользователя: {}, способ: {}",
                command.getUserId(), command.getPaymentMethod());

        // ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #2: Сохраняем способ оплаты в сессии
        savePaymentMethodToSession(command.getUserId(), command.getPaymentMethod());

        // Обновляем состояние сессии
        sessionService.updateSessionState(command.getUserId(),
                shit.back.model.UserSession.SessionState.TOPPING_UP_BALANCE);

        String message = String.format("""
                💰 <b>Пополнение баланса</b>

                💳 <b>Способ:</b> %s

                💸 <b>Выберите удобную сумму:</b>
                """, getPaymentMethodDisplayName(command.getPaymentMethod()));

        // Создаем клавиатуру с предустановленными суммами
        var keyboard = new TelegramKeyboardBuilder()
                .addButton("💵 10", "topup_amount_10")
                .addButton("💵 25", "topup_amount_25")
                .addButton("💵 50", "topup_amount_50")
                .newRow()
                .addButton("💵 100", "topup_amount_100")
                .addButton("💵 250", "topup_amount_250")
                .addButton("💵 500", "topup_amount_500")
                .newRow()
                .addButton("✏️ Свою сумму", "custom_amount")
                .addButton("🔙 Назад", "topup_balance")
                .build();

        return TelegramResponse.builder()
                .successful(true)
                .message(message)
                .uiType("TOPUP_AMOUNT_INPUT")
                .uiData(command.getPaymentMethod())
                .data(keyboard)
                .build();
    }

    /**
     * ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #2: Обработка команды пополнения с сохранением способа
     * оплаты
     */
    private TelegramResponse handleTopupWithAmount(TopupBalanceCommand command) throws Exception {
        // ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #2: Если способ оплаты не указан, получаем из сессии
        String paymentMethod = command.getPaymentMethod();
        if (paymentMethod == null || "DEFAULT".equals(paymentMethod)) {
            paymentMethod = getPaymentMethodFromSession(command.getUserId());
        }

        // Если способ оплаты все еще не найден, возвращаем к выбору способа оплаты
        if (paymentMethod == null) {
            log.warn("⚠️ Способ оплаты не найден для пользователя {}, возвращаем к выбору", command.getUserId());
            return showPaymentMethods(command.getUserId());
        }

        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Всегда показываем подтверждение (логика изменена в
        // TelegramHandlerFacade)
        return showTopupConfirmation(command, paymentMethod);
    }

    /**
     * КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Отдельный метод для показа подтверждения
     */
    private TelegramResponse showTopupConfirmation(TopupBalanceCommand command, String paymentMethod) {
        String message = String.format("""
                ✅ <b>Подтверждение пополнения</b>

                💰 <b>Сумма:</b> %s USD
                💳 <b>Способ:</b> %s

                💡 Нажмите "Подтвердить" для продолжения
                """,
                command.getAmount(),
                getPaymentMethodDisplayName(paymentMethod));

        // Обновляем состояние сессии
        sessionService.updateSessionState(command.getUserId(),
                shit.back.model.UserSession.SessionState.TOPPING_UP_BALANCE);

        // Создаем клавиатуру подтверждения
        var keyboard = new TelegramKeyboardBuilder()
                .addButton("✅ Подтвердить", "confirm_topup_" + command.getAmount())
                .addButton("✏️ Изменить сумму", "topup_balance")
                .newRow()
                .addButton("❌ Отменить", "cancel_topup")
                .addButton("🔙 Назад", "show_balance")
                .build();

        return TelegramResponse.builder()
                .successful(true)
                .message(message)
                .uiType("TOPUP_CONFIRMATION")
                .uiData(command)
                .data(keyboard)
                .build();
    }

    /**
     * ИСПРАВЛЕНО: Получение способа оплаты из сессии с кэшированием
     */
    private String getPaymentMethodFromSession(Long userId) {
        try {
            log.debug("🔍 Получение способа оплаты для пользователя {}", userId);

            // ИСПРАВЛЕНИЕ: Сначала проверяем кэш
            Optional<CachedUserState> cachedOpt = getCachedState(userId);
            if (cachedOpt.isPresent()) {
                String paymentMethod = cachedOpt.get().paymentMethod;
                if (paymentMethod != null && !paymentMethod.trim().isEmpty() && !"DEFAULT".equals(paymentMethod)) {
                    log.debug("🎯 Найден кэшированный способ оплаты: {} для пользователя {}", paymentMethod, userId);
                    return paymentMethod;
                }
            }

            // ИСПРАВЛЕНИЕ: Если не в кэше, получаем из сессии
            Optional<shit.back.model.UserSession> sessionOpt = sessionService.getSession(userId);
            if (sessionOpt.isPresent()) {
                shit.back.model.UserSession session = sessionOpt.get();
                String paymentType = session.getPaymentType();

                if (paymentType != null && !paymentType.trim().isEmpty() && !"DEFAULT".equals(paymentType)) {
                    String normalizedMethod = normalizePaymentMethod(paymentType);

                    // ИСПРАВЛЕНИЕ: Кэшируем полученное значение
                    cacheUserState(userId, normalizedMethod, session.getState());

                    log.debug("✅ Найден способ оплаты: {} для пользователя {}", normalizedMethod, userId);
                    return normalizedMethod;
                }
            }

            log.debug("⚠️ Способ оплаты не найден для пользователя {}", userId);
            return null;
        } catch (Exception e) {
            log.error("❌ Ошибка при получении способа оплаты из сессии: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Нормализация callback данных к простому формату способа оплаты
     */
    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }

        return switch (paymentMethod.toLowerCase()) {
            case "payment_crypto" -> "crypto";
            case "payment_yoomoney" -> "yoomoney";
            case "payment_uzs" -> "uzs";
            default -> paymentMethod;
        };
    }

    /**
     * ИСПРАВЛЕНИЕ ПРОБЛЕМЫ #4: User-friendly названия способов оплаты
     */
    private String getPaymentMethodDisplayName(String paymentMethod) {
        if (paymentMethod == null) {
            return "Не выбран";
        }

        // 🔍 ДИАГНОСТИЧЕСКИЙ ЛОГ #1: Проверяем маппинг способов оплаты
        log.info("🔍 ДИАГНОСТИКА МАППИНГ: Входной paymentMethod='{}', приведенный к нижнему регистру='{}'",
                paymentMethod, paymentMethod.toLowerCase());

        String result = switch (paymentMethod.toLowerCase()) {
            case "yoomoney", "payment_yoomoney" -> "💳 YooMoney";
            case "crypto", "payment_crypto" -> "₿ Криптовалюта";
            case "uzs", "payment_uzs" -> "💳 UZS карта";
            case "uzs_payment" -> { // 🔍 ДИАГНОСТИКА: Добавляем отсутствующий case
                log.info("🔍 ДИАГНОСТИКА МАППИНГ: Найден UZS_PAYMENT - это была ПРОБЛЕМА #1!");
                yield "💳 UZS карта";
            }
            case "ton" -> "₿ Криптовалюта";
            case "yookassa" -> "💳 YooMoney";
            default -> {
                log.warn("🚨 ДИАГНОСТИКА МАППИНГ: Неизвестный способ оплаты '{}' - используем default значение",
                        paymentMethod);
                yield "� Способ оплаты";
            }
        };

        log.info("🔍 ДИАГНОСТИКА МАППИНГ: Результат маппинга: '{}' -> '{}'", paymentMethod, result);
        return result;
    }

    /**
     * КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Сохранение способа оплаты в сессии с гарантированным
     * сохранением в PostgreSQL
     */
    private void savePaymentMethodToSession(Long userId, String paymentMethod) {
        try {
            log.info("💾 ДИАГНОСТИКА: Попытка сохранить способ оплаты '{}' для пользователя {}", paymentMethod, userId);

            // ИСПРАВЛЕНИЕ ROOT CAUSE: Получаем или создаем сессию если она не существует
            shit.back.model.UserSession session = sessionService.getOrCreateSession(userId, null, null, null);

            // Логируем состояние ПЕРЕД изменением
            log.info("💾 ДИАГНОСТИКА: ПЕРЕД изменением - PaymentType: '{}', State: {}",
                    session.getPaymentType(), session.getState());

            // Нормализуем способ оплаты перед сохранением
            String normalizedMethod = normalizePaymentMethod(paymentMethod);
            String finalPaymentMethod = normalizedMethod != null ? normalizedMethod : paymentMethod;

            // Используем встроенный метод setPaymentType для корректного обновления
            // состояния
            session.setPaymentType(finalPaymentMethod);

            // Логируем состояние ПОСЛЕ изменения
            log.info("💾 ДИАГНОСТИКА: ПОСЛЕ изменения - PaymentType: '{}', State: {}",
                    session.getPaymentType(), session.getState());

            // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Принудительное сохранение в PostgreSQL!
            sessionService.createOrUpdateSessionEntity(session);
            log.info("✅ ДИАГНОСТИКА: Изменения PaymentType успешно сохранены в PostgreSQL для пользователя {}", userId);

            log.debug("💾 Способ оплаты сохранен: {} → {} для пользователя {}",
                    paymentMethod, finalPaymentMethod, userId);

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА: Не удалось сохранить способ оплаты для пользователя {}: {}",
                    userId, e.getMessage(), e);
            throw new RuntimeException("Failed to save payment method to session", e);
        }
    }

    /**
     * ИСПРАВЛЕНИЕ: Внутренний класс для кэширования состояний пользователей
     */
    private static class CachedUserState {
        final String paymentMethod;
        final shit.back.model.UserSession.SessionState state;
        final long timestamp;

        CachedUserState(String paymentMethod, shit.back.model.UserSession.SessionState state) {
            this.paymentMethod = paymentMethod;
            this.state = state;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return (System.currentTimeMillis() - timestamp) > ttlMs;
        }
    }

    /**
     * ИСПРАВЛЕНИЕ: Получение кэшированного состояния пользователя
     */
    private Optional<CachedUserState> getCachedState(Long userId) {
        CachedUserState cached = stateCache.get(userId);
        if (cached != null && !cached.isExpired(CACHE_TTL_MS)) {
            log.debug("🎯 Получено кэшированное состояние для пользователя {}: {}", userId, cached.paymentMethod);
            return Optional.of(cached);
        }

        if (cached != null) {
            stateCache.remove(userId); // Удаляем устаревшую запись
        }
        return Optional.empty();
    }

    /**
     * ИСПРАВЛЕНИЕ: Кэширование состояния пользователя
     */
    private void cacheUserState(Long userId, String paymentMethod, shit.back.model.UserSession.SessionState state) {
        stateCache.put(userId, new CachedUserState(paymentMethod, state));
        log.debug("💾 Кэшировано состояние для пользователя {}: {}", userId, paymentMethod);
    }
}