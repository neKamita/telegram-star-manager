package shit.back.entity;

/**
 * Статусы транзакций баланса пользователя
 * 
 * PENDING - транзакция создана, ожидает обработки
 * COMPLETED - транзакция успешно завершена
 * FAILED - транзакция не удалась
 * CANCELLED - транзакция отменена
 */
public enum TransactionStatus {
    PENDING, // Ожидает обработки
    COMPLETED, // Завершено
    FAILED, // Не удалось
    CANCELLED // Отменено
}