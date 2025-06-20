package shit.back.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import shit.back.service.SystemLogCaptureService;

/**
 * Logback Appender для захвата важных системных логов
 * и передачи их в SystemLogCaptureService
 */
@Component
public class SystemLogAppender extends AppenderBase<ILoggingEvent> implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    private SystemLogCaptureService systemLogCaptureService;

    // Флаг для предотвращения рекурсивного логирования
    private volatile boolean initialized = false;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    protected void append(ILoggingEvent event) {
        // Инициализация сервиса (ленивая загрузка)
        if (!initialized && applicationContext != null) {
            try {
                systemLogCaptureService = applicationContext.getBean(SystemLogCaptureService.class);
                initialized = true;
            } catch (Exception e) {
                // Сервис еще не готов, пропускаем
                return;
            }
        }

        if (systemLogCaptureService == null || !initialized) {
            return;
        }

        try {
            // Предотвращаем рекурсивное логирование от нашего собственного сервиса
            String loggerName = event.getLoggerName();
            if (loggerName.contains("SystemLogCaptureService") ||
                    loggerName.contains("UserActivityLogService") ||
                    loggerName.contains("SystemLogAppender")) {
                return;
            }

            String level = event.getLevel().toString();
            String message = event.getFormattedMessage();
            Throwable throwable = event.getThrowableProxy() != null
                    ? new RuntimeException(event.getThrowableProxy().getMessage())
                    : null;

            // Фильтрация: захватываем только важные логи
            if (shouldCaptureLog(loggerName, level, message)) {
                systemLogCaptureService.captureGenericLog(level, message, loggerName, throwable);
            }

        } catch (Exception e) {
            // Не логируем ошибки аппендера, чтобы избежать рекурсии
            System.err.println("SystemLogAppender error: " + e.getMessage());
        }
    }

    /**
     * Определяет, нужно ли захватывать данный лог
     */
    private boolean shouldCaptureLog(String loggerName, String level, String message) {
        // Захватываем только ERROR и WARN уровни, плюс некоторые INFO
        if ("ERROR".equals(level) || "WARN".equals(level)) {
            return true;
        }

        // Специальные случаи для INFO логов
        if ("INFO".equals(level)) {
            return shouldCaptureInfoLog(loggerName, message);
        }

        return false;
    }

    /**
     * Определяет, нужно ли захватывать INFO лог
     */
    private boolean shouldCaptureInfoLog(String loggerName, String message) {
        // Spring DispatcherServlet - только для админ, payment, webhook endpoints
        if (loggerName.contains("DispatcherServlet")) {
            return message.contains("/admin") ||
                    message.contains("/payment") ||
                    message.contains("/webhook") ||
                    message.contains("POST") ||
                    message.contains("PUT") ||
                    message.contains("DELETE");
        }

        // BackgroundMetricsService - важные метрики
        if (loggerName.contains("BackgroundMetricsService")) {
            return message.contains("metrics") ||
                    message.contains("performance") ||
                    message.contains("completed") ||
                    message.contains("started");
        }

        // AdminDashboardService - операции с дашбордом
        if (loggerName.contains("AdminDashboardService")) {
            return message.contains("dashboard") ||
                    message.contains("cache") ||
                    message.contains("updated") ||
                    message.contains("refreshed");
        }

        // Hibernate SQL - только важные операции
        if (loggerName.contains("hibernate") || loggerName.contains("SQL")) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("insert") ||
                    lowerMessage.contains("update") ||
                    lowerMessage.contains("delete") ||
                    (lowerMessage.contains("select") &&
                            (lowerMessage.contains("user_activity") ||
                                    lowerMessage.contains("orders") ||
                                    lowerMessage.contains("payments")));
        }

        // Приложение - важные события
        if (loggerName.startsWith("shit.back")) {
            return message.contains("started") ||
                    message.contains("stopped") ||
                    message.contains("initialized") ||
                    message.contains("shutdown");
        }

        return false;
    }

    @Override
    public void start() {
        super.start();
        System.out.println("SystemLogAppender started");
    }

    @Override
    public void stop() {
        super.stop();
        System.out.println("SystemLogAppender stopped");
    }
}