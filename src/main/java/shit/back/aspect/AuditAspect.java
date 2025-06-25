package shit.back.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shit.back.annotation.Auditable;
import shit.back.security.SecurityContextManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Аспект для автоматического аудита операций
 * Реализация Security Patterns (Week 3-4) - AuditAspect для автоматического
 * логирования
 * 
 * Функции:
 * - Автоматическое логирование вызовов методов
 * - Трекинг времени выполнения операций
 * - Аудит критических операций безопасности
 * - Сбор метрик производительности
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring - Security Patterns Implementation
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    @Autowired(required = false)
    private SecurityContextManager securityContextManager;

    private final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();

    /**
     * Pointcut для всех методов с аннотацией @Auditable
     */
    @Pointcut("@annotation(shit.back.annotation.Auditable)")
    public void auditableMethod() {
    }

    /**
     * Pointcut для всех методов сервисов администрирования
     */
    @Pointcut("execution(* shit.back.service.Admin*.*(..))")
    public void adminServiceMethods() {
    }

    /**
     * Pointcut для всех методов контроллеров администрирования
     */
    @Pointcut("execution(* shit.back.controller.admin.*.*(..))")
    public void adminControllerMethods() {
    }

    /**
     * Pointcut для методов безопасности
     */
    @Pointcut("execution(* shit.back.security.*.*(..))")
    public void securityMethods() {
    }

    /**
     * Around advice для аудируемых методов
     */
    @Around("auditableMethod() && @annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String methodName = getMethodSignature(joinPoint);
        String userId = getCurrentUserId();
        long startTime = System.currentTimeMillis();

        // Логирование начала операции
        log.info("🔍 АУДИТ [{}]: Начало операции '{}' | Пользователь: {} | Тип: {} | Время: {}",
                generateAuditId(),
                methodName,
                userId,
                auditable.auditType().name(),
                LocalDateTime.now());

        Object result = null;
        Exception exception = null;

        try {
            // Выполнение целевого метода
            result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;

            // Логирование успешного завершения
            log.info(
                    "✅ АУДИТ: Операция '{}' выполнена успешно | Пользователь: {} | Время выполнения: {}ms | Описание: {}",
                    methodName,
                    userId,
                    executionTime,
                    auditable.description());

            // Обновление метрик
            updateOperationMetrics(methodName, executionTime, true);

            return result;

        } catch (Exception e) {
            exception = e;
            long executionTime = System.currentTimeMillis() - startTime;

            // Логирование ошибки
            log.error("❌ АУДИТ: Ошибка в операции '{}' | Пользователь: {} | Время выполнения: {}ms | Ошибка: {}",
                    methodName,
                    userId,
                    executionTime,
                    e.getMessage());

            // Обновление метрик с ошибкой
            updateOperationMetrics(methodName, executionTime, false);

            // Критический аудит для ошибок безопасности
            if (auditable.auditType() == Auditable.AuditType.SECURITY) {
                logCriticalSecurityEvent(methodName, userId, e);
            }

            throw e;
        }
    }

    /**
     * Before advice для административных операций
     */
    @Before("adminServiceMethods() || adminControllerMethods()")
    public void auditAdminOperation(JoinPoint joinPoint) {
        String methodName = getMethodSignature(joinPoint);
        String userId = getCurrentUserId();
        Object[] args = joinPoint.getArgs();

        log.info("🔐 АДМИН-АУДИТ: Вызов административной операции '{}' | Пользователь: {} | Аргументы: {} | Время: {}",
                methodName,
                userId,
                sanitizeArguments(args),
                LocalDateTime.now());
    }

    /**
     * After advice для операций безопасности
     */
    // Temporarily disabled to avoid recursion with SecurityContextManager.getCurrentUserId()
    // @AfterReturning(pointcut = "securityMethods()", returning = "result")
    public void auditSecurityOperationDisabled(JoinPoint joinPoint, Object result) {
        String methodName = getMethodSignature(joinPoint);
        String userId = getCurrentUserId();

        log.info(
                "🛡️ БЕЗОПАСНОСТЬ-АУДИТ: Завершена операция безопасности '{}' | Пользователь: {} | Результат: {} | Время: {}",
                methodName,
                userId,
                sanitizeResult(result),
                LocalDateTime.now());
    }

    /**
     * After throwing advice для отслеживания исключений
     */
    @AfterThrowing(pointcut = "adminServiceMethods() || adminControllerMethods() || securityMethods()", throwing = "exception")
    public void auditException(JoinPoint joinPoint, Exception exception) {
        String methodName = getMethodSignature(joinPoint);
        String userId = getCurrentUserId();

        log.error(
                "💥 АУДИТ-ИСКЛЮЧЕНИЕ: Исключение в методе '{}' | Пользователь: {} | Тип исключения: {} | Сообщение: {} | Время: {}",
                methodName,
                userId,
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                LocalDateTime.now());
    }

    /**
     * Получение метрик операций
     */
    public Map<String, OperationMetrics> getOperationMetrics() {
        return new ConcurrentHashMap<>(operationMetrics);
    }

    /**
     * Очистка метрик операций
     */
    public void clearMetrics() {
        operationMetrics.clear();
        log.info("🧹 АУДИТ: Метрики операций очищены");
    }

    // Приватные методы

    private String getCurrentUserId() {
        if (securityContextManager != null) {
            return securityContextManager.getCurrentUserId().orElse("ANONYMOUS");
        }
        return "SYSTEM";
    }

    private String getMethodSignature(JoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName() + "." +
                joinPoint.getSignature().getName();
    }

    private String generateAuditId() {
        return "AUD_" + System.currentTimeMillis() + "_" +
                Integer.toHexString((int) (Math.random() * 0xFFFF)).toUpperCase();
    }

    private void updateOperationMetrics(String methodName, long executionTime, boolean success) {
        operationMetrics.computeIfAbsent(methodName, k -> new OperationMetrics())
                .update(executionTime, success);
    }

    private void logCriticalSecurityEvent(String methodName, String userId, Exception exception) {
        log.error(
                "🚨 КРИТИЧЕСКИЙ АУДИТ БЕЗОПАСНОСТИ: Ошибка в методе безопасности '{}' | Пользователь: {} | Исключение: {} | ТРЕБУЕТСЯ НЕМЕДЛЕННОЕ ВНИМАНИЕ!",
                methodName,
                userId,
                exception.getClass().getSimpleName());
    }

    private String sanitizeArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        // Санитизация аргументов - скрываем чувствительные данные
        String[] sanitized = Arrays.stream(args)
                .map(arg -> {
                    if (arg == null)
                        return "null";
                    String str = arg.toString();
                    // Скрываем возможные пароли, токены и т.д.
                    if (str.toLowerCase().contains("password") ||
                            str.toLowerCase().contains("token") ||
                            str.toLowerCase().contains("secret")) {
                        return "[SANITIZED]";
                    }
                    // Ограничиваем длину
                    return str.length() > 100 ? str.substring(0, 100) + "..." : str;
                })
                .toArray(String[]::new);

        return Arrays.toString(sanitized);
    }

    private String sanitizeResult(Object result) {
        if (result == null)
            return "null";

        String str = result.toString();
        // Ограничиваем длину результата в логах
        return str.length() > 200 ? str.substring(0, 200) + "..." : str;
    }

    /**
     * Класс для хранения метрик операций
     */
    public static class OperationMetrics {
        private long totalCalls = 0;
        private long successfulCalls = 0;
        private long totalExecutionTime = 0;
        private long minExecutionTime = Long.MAX_VALUE;
        private long maxExecutionTime = 0;
        private LocalDateTime lastCall;

        public synchronized void update(long executionTime, boolean success) {
            totalCalls++;
            totalExecutionTime += executionTime;

            if (success) {
                successfulCalls++;
            }

            minExecutionTime = Math.min(minExecutionTime, executionTime);
            maxExecutionTime = Math.max(maxExecutionTime, executionTime);
            lastCall = LocalDateTime.now();
        }

        // Геттеры
        public long getTotalCalls() {
            return totalCalls;
        }

        public long getSuccessfulCalls() {
            return successfulCalls;
        }

        public double getSuccessRate() {
            return totalCalls == 0 ? 0.0 : (double) successfulCalls / totalCalls * 100;
        }

        public double getAverageExecutionTime() {
            return totalCalls == 0 ? 0.0 : (double) totalExecutionTime / totalCalls;
        }

        public long getMinExecutionTime() {
            return minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime;
        }

        public long getMaxExecutionTime() {
            return maxExecutionTime;
        }

        public LocalDateTime getLastCall() {
            return lastCall;
        }
    }
}