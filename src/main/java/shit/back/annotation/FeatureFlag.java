package shit.back.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureFlag {
    
    /**
     * Название флага функции
     */
    String value();
    
    /**
     * Метод fallback, который будет вызван если флаг отключен
     */
    String fallback() default "";
    
    /**
     * Должен ли флаг проверяться для конкретного пользователя
     */
    boolean userSpecific() default true;
    
    /**
     * Описание функции для документации
     */
    String description() default "";
    
    /**
     * Версия, в которой появилась функция
     */
    String since() default "";
    
    /**
     * Является ли функция экспериментальной
     */
    boolean experimental() default false;
    
    /**
     * Требует ли функция особых разрешений
     */
    String[] requiredPermissions() default {};
}
