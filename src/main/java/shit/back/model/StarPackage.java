package shit.back.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StarPackage {
    private int stars;
    private BigDecimal originalPrice;
    private BigDecimal discountedPrice;
    private int discountPercent;
    private String packageId;

    public int getStars() {
        return stars;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public BigDecimal getDiscountedPrice() {
        return discountedPrice;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public String getPackageId() {
        return packageId;
    }

    public static StarPackage createPackage(int stars, BigDecimal originalPrice, int discountPercent) {
        StarPackage pkg = new StarPackage();
        pkg.stars = stars;
        pkg.originalPrice = originalPrice;
        pkg.discountPercent = discountPercent;

        BigDecimal discount = originalPrice.multiply(BigDecimal.valueOf(discountPercent))
                .divide(BigDecimal.valueOf(100));
        pkg.discountedPrice = originalPrice.subtract(discount);
        pkg.packageId = "STARS_" + stars;

        return pkg;
    }

    public BigDecimal getSavings() {
        return originalPrice.subtract(discountedPrice);
    }
}
