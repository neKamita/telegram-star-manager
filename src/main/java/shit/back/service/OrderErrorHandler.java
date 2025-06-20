package shit.back.service;

public class OrderErrorHandler {

    public static RuntimeException handleOrderCreationException(Exception e) {
        return new RuntimeException("Не удалось создать заказ", e);
    }

    public static RuntimeException handleOrderPaymentException(Exception e) {
        return new RuntimeException("Не удалось обработать оплату заказа", e);
    }

    public static RuntimeException handleOrderCancelException(Exception e) {
        return new RuntimeException("Не удалось отменить заказ", e);
    }
}