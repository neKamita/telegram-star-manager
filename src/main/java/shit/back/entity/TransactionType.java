package shit.back.entity;

/**
 * Типы транзакций баланса пользователя
 * 
 * DEPOSIT - пополнение баланса
 * WITHDRAWAL - снятие средств (не используется в текущей версии)
 * PURCHASE - покупка пакета звезд
 * REFUND - возврат средств
 * ADJUSTMENT - корректировка баланса (админская операция)
 */
public enum TransactionType {
    DEPOSIT, // Пополнение баланса
    WITHDRAWAL, // Снятие средств
    PURCHASE, // Покупка пакета звезд
    REFUND, // Возврат средств
    ADJUSTMENT // Корректировка баланса
}