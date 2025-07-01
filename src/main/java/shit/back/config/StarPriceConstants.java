package shit.back.config;

import shit.back.domain.balance.valueobjects.Money;

import java.math.BigDecimal;

/**
 * Константы цен на звезды Telegram
 * 
 * Централизованный источник цен для обеспечения консистентности
 * по всему приложению. Следует принципам DRY и SOLID.
 */
public final class StarPriceConstants {

    // Приватный конструктор для utility класса
    private StarPriceConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // Константы цен на звезды (в долларах США)
    public static final BigDecimal STARS_100_PRICE = new BigDecimal("1.00");
    public static final BigDecimal STARS_500_PRICE = new BigDecimal("4.50");
    public static final BigDecimal STARS_1000_PRICE = new BigDecimal("8.00");
    public static final BigDecimal STARS_2500_PRICE = new BigDecimal("25.00");
    public static final BigDecimal STARS_5000_PRICE = new BigDecimal("35.00");
    public static final BigDecimal STARS_10000_PRICE = new BigDecimal("65.00");

    // Методы для получения цены по количеству звезд
    public static Money getPriceForStars(int starCount) {
        return switch (starCount) {
            case 100 -> Money.of(STARS_100_PRICE);
            case 500 -> Money.of(STARS_500_PRICE);
            case 1000 -> Money.of(STARS_1000_PRICE);
            case 2500 -> Money.of(STARS_2500_PRICE);
            case 5000 -> Money.of(STARS_5000_PRICE);
            case 10000 -> Money.of(STARS_10000_PRICE);
            default -> throw new IllegalArgumentException("Неподдерживаемое количество звезд: " + starCount);
        };
    }

    // Проверка поддерживаемых пакетов
    public static boolean isSupportedStarCount(int starCount) {
        return starCount == 100 || starCount == 500 || starCount == 1000 ||
                starCount == 2500 || starCount == 5000 || starCount == 10000;
    }

    // Получение всех доступных количеств звезд
    public static int[] getAllSupportedStarCounts() {
        return new int[] { 100, 500, 1000, 2500, 5000, 10000 };
    }
}