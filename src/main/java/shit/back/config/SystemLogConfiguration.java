package shit.back.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import shit.back.service.SystemLogCaptureService;

/**
 * Конфигурация для инициализации системы захвата логов
 */
@Configuration
public class SystemLogConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private SystemLogCaptureService systemLogCaptureService;

    @Autowired
    private SystemLogAppender systemLogAppender;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        initializeSystemLogCapture();
    }

    /**
     * Инициализация системы захвата логов после запуска приложения
     */
    private void initializeSystemLogCapture() {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            // Настраиваем SystemLogAppender с ApplicationContext
            systemLogAppender.setApplicationContext(applicationContext);
            systemLogAppender.setContext(loggerContext);
            systemLogAppender.start();

            // Добавляем аппендер к root logger
            Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(systemLogAppender);

            // Логируем успешную инициализацию
            org.slf4j.Logger log = LoggerFactory.getLogger(SystemLogConfiguration.class);
            log.info("Система захвата backend логов успешно инициализирована");
            log.debug("SystemLogAppender добавлен к root logger");

            // Тестовое сообщение для проверки работы системы
            log.info("Тестовое сообщение для проверки захвата системных логов");

        } catch (Exception e) {
            org.slf4j.Logger log = LoggerFactory.getLogger(SystemLogConfiguration.class);
            log.error("Ошибка инициализации системы захвата логов: {}", e.getMessage(), e);
        }
    }

    /**
     * Ручной захват тестового лога (для проверки работы системы)
     */
    public void captureTestLog() {
        try {
            systemLogCaptureService.captureGenericLog(
                    "INFO",
                    "Тестовый системный лог от SystemLogConfiguration",
                    this.getClass().getName(),
                    null);
        } catch (Exception e) {
            org.slf4j.Logger log = LoggerFactory.getLogger(SystemLogConfiguration.class);
            log.error("Ошибка при захвате тестового лога: {}", e.getMessage(), e);
        }
    }
}