package shit.back.service;

import org.springframework.stereotype.Service;
import shit.back.model.StarPackage;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class PriceService {
    
    private static final int DEFAULT_DISCOUNT = 15; // 15% скидка
    
    private final List<StarPackage> packages = Arrays.asList(
        StarPackage.createPackage(100, new BigDecimal("10.00"), DEFAULT_DISCOUNT),
        StarPackage.createPackage(500, new BigDecimal("50.00"), DEFAULT_DISCOUNT),
        StarPackage.createPackage(1000, new BigDecimal("100.00"), DEFAULT_DISCOUNT),
        StarPackage.createPackage(2500, new BigDecimal("250.00"), DEFAULT_DISCOUNT),
        StarPackage.createPackage(5000, new BigDecimal("500.00"), DEFAULT_DISCOUNT)
    );
    
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
            pkg.getDiscountPercent()
        );
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
