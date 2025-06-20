package shit.back.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для методов, требующих аудита
 * Используется с AuditAspect для автоматического логирования операций
 * 
 * Реализация Security Patterns (Week 3-4)
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring - Security Patterns Implementation
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * Описание операции для аудита
     */
    String description() default "";

    /**
     * Тип аудита
     */
    AuditType auditType() default AuditType.BUSINESS;

    /**
     * Включать ли параметры в лог
     */
    boolean logParameters() default true;

    /**
     * Включать ли результат в лог
     */
    boolean logResult() default false;

    /**
     * Типы аудита
     */
    enum AuditType {
        /**
         * Бизнес-операции
         */
        BUSINESS("Бизнес-операция"),

        /**
         * Операции безопасности
         */
        SECURITY("Операция безопасности"),

        /**
         * Административные операции
         */
        ADMIN("Административная операция"),

        /**
         * Критические операции
         */
        CRITICAL("Критическая операция"),

        /**
         * Операции с данными
         */
        DATA("Операция с данными");

        private final String description;

        AuditType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}