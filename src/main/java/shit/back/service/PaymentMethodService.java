package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shit.back.config.PaymentConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для управления методами платежей
 * 
 * Содержит бизнес-логику для определения доступных платежных методов,
 * включая логику работы с тестовыми платежами
 */
@Slf4j
@Service
public class PaymentMethodService {

    @Autowired
    private PaymentConfigurationProperties paymentConfig;

    @Autowired(required = false)
    private TestPaymentService testPaymentService;

    /**
     * Проверить, включена ли хотя бы одна платежная система
     */
    public boolean hasEnabledPaymentMethods() {
        boolean hasRegularMethods = paymentConfig.getTon().getEnabled() ||
                paymentConfig.getYookassa().getEnabled() ||
                paymentConfig.getFragment().getEnabled() ||
                paymentConfig.getUzsPayment().getEnabled();

        // Включаем TEST метод если тестовый режим активен
        boolean hasTestMethod = testPaymentService != null && testPaymentService.isTestModeEnabled();

        return hasRegularMethods || hasTestMethod;
    }

    /**
     * Получить список включенных платежных методов
     */
    public String[] getEnabledPaymentMethods() {
        List<String> methods = new ArrayList<>();

        if (paymentConfig.getTon().getEnabled())
            methods.add("TON");
        if (paymentConfig.getYookassa().getEnabled())
            methods.add("YooKassa");
        if (paymentConfig.getFragment().getEnabled())
            methods.add("Fragment");
        if (paymentConfig.getUzsPayment().getEnabled())
            methods.add("UzsPayment");

        // Добавляем TEST метод если тестовый режим активен
        if (testPaymentService != null && testPaymentService.isTestModeEnabled()) {
            methods.add("TEST");
        }

        return methods.toArray(new String[0]);
    }

    /**
     * Проверить, что все необходимые настройки заполнены
     */
    public boolean isValidConfiguration() {
        if (!hasEnabledPaymentMethods()) {
            return false;
        }

        if (paymentConfig.getTon().getEnabled() &&
                (paymentConfig.getTon().getApiKey() == null || paymentConfig.getTon().getApiKey().isEmpty())) {
            return false;
        }

        if (paymentConfig.getYookassa().getEnabled() &&
                (paymentConfig.getYookassa().getShopId() == null
                        || paymentConfig.getYookassa().getShopId().isEmpty())) {
            return false;
        }

        if (paymentConfig.getFragment().getEnabled() &&
                (paymentConfig.getFragment().getToken() == null || paymentConfig.getFragment().getToken().isEmpty())) {
            return false;
        }

        if (paymentConfig.getUzsPayment().getEnabled() &&
                (paymentConfig.getUzsPayment().getMerchantId() == null
                        || paymentConfig.getUzsPayment().getMerchantId().isEmpty())) {
            return false;
        }

        return true;
    }

    /**
     * Проверить, поддерживается ли указанный метод платежа
     */
    public boolean isPaymentMethodSupported(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            return false;
        }

        String[] enabledMethods = getEnabledPaymentMethods();
        for (String method : enabledMethods) {
            if (method.equalsIgnoreCase(paymentMethod)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Получить конфигурацию для конкретного метода платежа
     */
    public PaymentConfigurationProperties getPaymentConfig() {
        return paymentConfig;
    }
}