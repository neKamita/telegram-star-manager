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
    
    public static StarPackage createPackage(int stars, BigDecimal originalPrice, int discountPercent) {
        StarPackage pkg = new StarPackage();
        pkg.setStars(stars);
        pkg.setOriginalPrice(originalPrice);
        pkg.setDiscountPercent(discountPercent);
        
        BigDecimal discount = originalPrice.multiply(BigDecimal.valueOf(discountPercent)).divide(BigDecimal.valueOf(100));
        pkg.setDiscountedPrice(originalPrice.subtract(discount));
        pkg.setPackageId("STARS_" + stars);
        
        return pkg;
    }
    
    public BigDecimal getSavings() {
        return originalPrice.subtract(discountedPrice);
    }
}
