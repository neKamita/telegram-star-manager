package shit.back.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shit.back.annotation.FeatureFlag;
import shit.back.service.FeatureFlagService;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
public class FeatureFlagAspect {
    
    @Autowired
    private FeatureFlagService featureFlagService;
    
    @Around("@annotation(shit.back.annotation.FeatureFlag)")
    public Object aroundFeatureFlaggedMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        FeatureFlag featureFlag = method.getAnnotation(FeatureFlag.class);
        
        String flagName = featureFlag.value();
        String userId = extractUserIdFromArgs(joinPoint.getArgs());
        
        boolean isEnabled;
        if (featureFlag.userSpecific() && userId != null) {
            isEnabled = featureFlagService.isFeatureEnabled(flagName, userId);
        } else {
            isEnabled = featureFlagService.isFeatureEnabled(flagName);
        }
        
        if (isEnabled) {
            log.debug("Feature flag '{}' is enabled, proceeding with method execution", flagName);
            return joinPoint.proceed();
        } else {
            log.debug("Feature flag '{}' is disabled, executing fallback", flagName);
            return executeFallback(joinPoint, featureFlag);
        }
    }
    
    private String extractUserIdFromArgs(Object[] args) {
        // Пытаемся найти userId в аргументах метода
        for (Object arg : args) {
            if (arg != null) {
                // Проверяем разные типы объектов, которые могут содержать userId
                if (arg instanceof org.telegram.telegrambots.meta.api.objects.Message) {
                    org.telegram.telegrambots.meta.api.objects.Message message = 
                        (org.telegram.telegrambots.meta.api.objects.Message) arg;
                    return String.valueOf(message.getFrom().getId());
                }
                
                if (arg instanceof org.telegram.telegrambots.meta.api.objects.CallbackQuery) {
                    org.telegram.telegrambots.meta.api.objects.CallbackQuery callback = 
                        (org.telegram.telegrambots.meta.api.objects.CallbackQuery) arg;
                    return String.valueOf(callback.getFrom().getId());
                }
                
                // Проверяем наши модели
                if (arg instanceof shit.back.model.UserSession) {
                    shit.back.model.UserSession session = (shit.back.model.UserSession) arg;
                    return String.valueOf(session.getUserId());
                }
                
                // Если это просто String, который может быть userId
                if (arg instanceof String) {
                    String strArg = (String) arg;
                    try {
                        Long.parseLong(strArg);
                        return strArg; // Возможно, это userId
                    } catch (NumberFormatException e) {
                        // Не числовая строка, игнорируем
                    }
                }
                
                // Если это Long, который может быть userId
                if (arg instanceof Long) {
                    return String.valueOf(arg);
                }
            }
        }
        
        return null;
    }
    
    private Object executeFallback(ProceedingJoinPoint joinPoint, FeatureFlag featureFlag) throws Throwable {
        String fallbackMethod = featureFlag.fallback();
        
        if (fallbackMethod.isEmpty()) {
            // Если fallback не указан, возвращаем дефолтный ответ
            return createDefaultResponse(joinPoint);
        }
        
        try {
            // Пытаемся найти и выполнить fallback метод
            Object target = joinPoint.getTarget();
            Class<?> targetClass = target.getClass();
            Method[] methods = targetClass.getDeclaredMethods();
            
            for (Method method : methods) {
                if (method.getName().equals(fallbackMethod)) {
                    method.setAccessible(true);
                    
                    // Проверяем, соответствуют ли параметры
                    if (method.getParameterCount() == joinPoint.getArgs().length) {
                        return method.invoke(target, joinPoint.getArgs());
                    } else if (method.getParameterCount() == 0) {
                        return method.invoke(target);
                    }
                }
            }
            
            log.warn("Fallback method '{}' not found for feature flag '{}', using default response", 
                fallbackMethod, featureFlag.value());
            
        } catch (Exception e) {
            log.error("Error executing fallback method '{}' for feature flag '{}': {}", 
                fallbackMethod, featureFlag.value(), e.getMessage());
        }
        
        return createDefaultResponse(joinPoint);
    }
    
    private Object createDefaultResponse(ProceedingJoinPoint joinPoint) {
        Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
        
        // Для Telegram bot методов возвращаем стандартное сообщение
        if (returnType == org.telegram.telegrambots.meta.api.methods.send.SendMessage.class) {
            return createDisabledFeatureMessage(joinPoint);
        }
        
        if (returnType == org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText.class) {
            return createDisabledFeatureEditMessage(joinPoint);
        }
        
        // Для примитивных типов
        if (returnType == boolean.class || returnType == Boolean.class) {
            return false;
        }
        
        if (returnType == int.class || returnType == Integer.class) {
            return 0;
        }
        
        if (returnType == String.class) {
            return "Функция временно недоступна";
        }
        
        // Для void методов
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        
        // Для других типов возвращаем null
        return null;
    }
    
    private org.telegram.telegrambots.meta.api.methods.send.SendMessage createDisabledFeatureMessage(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        String chatId = extractChatId(args);
        
        org.telegram.telegrambots.meta.api.methods.send.SendMessage message = 
            new org.telegram.telegrambots.meta.api.methods.send.SendMessage();
        message.setChatId(chatId != null ? chatId : "0");
        message.setText("🚧 Эта функция временно недоступна. Мы работаем над её улучшением!");
        message.setParseMode("HTML");
        
        return message;
    }
    
    private org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText createDisabledFeatureEditMessage(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        String chatId = extractChatId(args);
        
        org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText editMessage = 
            new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText();
        editMessage.setChatId(chatId != null ? chatId : "0");
        editMessage.setText("🚧 Эта функция временно недоступна. Мы работаем над её улучшением!");
        editMessage.setParseMode("HTML");
        
        return editMessage;
    }
    
    private String extractChatId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof org.telegram.telegrambots.meta.api.objects.Message) {
                org.telegram.telegrambots.meta.api.objects.Message message = 
                    (org.telegram.telegrambots.meta.api.objects.Message) arg;
                return String.valueOf(message.getChatId());
            }
            
            if (arg instanceof org.telegram.telegrambots.meta.api.objects.CallbackQuery) {
                org.telegram.telegrambots.meta.api.objects.CallbackQuery callback = 
                    (org.telegram.telegrambots.meta.api.objects.CallbackQuery) arg;
                return String.valueOf(callback.getMessage().getChatId());
            }
            
            if (arg instanceof Long) {
                return String.valueOf(arg);
            }
            
            if (arg instanceof String) {
                try {
                    Long.parseLong((String) arg);
                    return (String) arg;
                } catch (NumberFormatException e) {
                    // Не числовая строка
                }
            }
        }
        
        return null;
    }
}
