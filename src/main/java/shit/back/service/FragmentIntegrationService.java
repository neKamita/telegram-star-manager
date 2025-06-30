package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shit.back.domain.balance.valueobjects.Money;

/**
 * Сервис интеграции с Fragment API для покупки звезд
 * Заглушка для обеспечения компиляции
 */
@Service
@Slf4j
public class FragmentIntegrationService {

    /**
     * Инициация покупки звезд через Fragment API
     */
    public void initiateStarPurchase(Long userId, Integer starCount, Money amount) {
        log.info("🚀 Fragment API: покупка звезд userId={}, stars={}, amount={}",
                userId, starCount, amount);
        // TODO: Реализовать реальную интеграцию с Fragment API
    }

    /**
     * Проверка статуса покупки
     */
    public String checkPurchaseStatus(String transactionId) {
        log.info("🔍 Fragment API: проверка статуса транзакции {}", transactionId);
        // TODO: Реализовать проверку статуса
        return "PENDING";
    }
}