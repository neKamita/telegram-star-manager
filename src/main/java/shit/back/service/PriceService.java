package shit.back.service;

import org.springframework.stereotype.Service;
import shit.back.config.StarPriceConstants;
import shit.back.model.StarPackage;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class PriceService {

    private static final int DEFAULT_DISCOUNT = 0; // Убираем скидку для консистентности цен

    private final List<StarPackage> packages = Arrays.asList(
            StarPackage.createPackage(100, StarPriceConstants.STARS_100_PRICE, DEFAULT_DISCOUNT),
            StarPackage.createPackage(500, StarPriceConstants.STARS_500_PRICE, DEFAULT_DISCOUNT),
            StarPackage.createPackage(1000, StarPriceConstants.STARS_1000_PRICE, DEFAULT_DISCOUNT),
            StarPackage.createPackage(2500, StarPriceConstants.STARS_2500_PRICE, DEFAULT_DISCOUNT),
            StarPackage.createPackage(5000, StarPriceConstants.STARS_5000_PRICE, DEFAULT_DISCOUNT),
            StarPackage.createPackage(10000, StarPriceConstants.STARS_10000_PRICE, DEFAULT_DISCOUNT));

    public List<StarPackage> getAllPackages() {
        return packages;
    }

    public Optional<StarPackage> getPackageById(String packageId) {
        return packages.stream()
                .filter(pkg -> pkg.getPackageId().equals(packageId))
                .findFirst();
    }

    public Optional<StarPackage> getPackageByStars(int stars) {
        return packages.stream()
                .filter(pkg -> pkg.getStars() == stars)
                .findFirst();
    }

    public String formatPriceComparison(StarPackage pkg) {
        return String.format(
                "💰 %d ⭐ - $%.2f (вместо $%.2f)\n💸 Экономия: $%.2f (%d%%)",
                pkg.getStars(),
                pkg.getDiscountedPrice(),
                pkg.getOriginalPrice(),
                pkg.getSavings(),
                pkg.getDiscountPercent());
    }

    public String formatShortPrice(StarPackage pkg) {
        return String.format("⭐ %d - $%.2f", pkg.getStars(), pkg.getDiscountedPrice());
    }

    public BigDecimal calculateTotalSavings() {
        return packages.stream()
                .map(StarPackage::getSavings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
