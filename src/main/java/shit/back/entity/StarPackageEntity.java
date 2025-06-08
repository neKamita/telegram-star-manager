package shit.back.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "star_packages", indexes = {
    @Index(name = "idx_star_packages_name", columnList = "name"),
    @Index(name = "idx_star_packages_enabled", columnList = "is_enabled"),
    @Index(name = "idx_star_packages_sort_order", columnList = "sort_order")
})
@Data
@NoArgsConstructor
public class StarPackageEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;
    
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "star_count", nullable = false)
    private Integer starCount;
    
    @Column(name = "original_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal originalPrice;
    
    @Column(name = "discount_percentage")
    private Integer discountPercentage;
    
    @Column(name = "final_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal finalPrice;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
    
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;
    
    @Column(name = "is_popular", nullable = false)
    private Boolean isPopular = false;
    
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, length = 20)
    private PackageType packageType = PackageType.STANDARD;
    
    @Column(name = "emoji", length = 10)
    private String emoji;
    
    @Column(name = "bonus_stars")
    private Integer bonusStars = 0;
    
    @Column(name = "min_order_count")
    private Integer minOrderCount; // Минимальное количество заказов для доступа
    
    @Column(name = "max_per_user")
    private Integer maxPerUser; // Максимум покупок на пользователя
    
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    
    @Column(name = "valid_until")
    private LocalDateTime validUntil;
    
    @Column(name = "total_sold", nullable = false)
    private Long totalSold = 0L;
    
    @Column(name = "revenue_generated", precision = 15, scale = 2, nullable = false)
    private BigDecimal revenueGenerated = BigDecimal.ZERO;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by", length = 50)
    private String createdBy = "ADMIN";
    
    @Column(name = "updated_by", length = 50)
    private String updatedBy = "ADMIN";
    
    public enum PackageType {
        STANDARD,
        PREMIUM,
        LIMITED_TIME,
        VIP,
        PROMOTIONAL
    }
    
    public StarPackageEntity(String name, String displayName, Integer starCount, 
                           BigDecimal originalPrice, Integer discountPercentage) {
        this.name = name;
        this.displayName = displayName;
        this.starCount = starCount;
        this.originalPrice = originalPrice;
        this.discountPercentage = discountPercentage;
        this.finalPrice = calculateFinalPrice();
        this.isEnabled = true;
    }
    
    public BigDecimal calculateFinalPrice() {
        if (discountPercentage == null || discountPercentage == 0) {
            return originalPrice;
        }
        
        BigDecimal discount = originalPrice.multiply(BigDecimal.valueOf(discountPercentage))
                                         .divide(BigDecimal.valueOf(100));
        return originalPrice.subtract(discount);
    }
    
    public void updateFinalPrice() {
        this.finalPrice = calculateFinalPrice();
    }
    
    public BigDecimal getDiscountAmount() {
        if (discountPercentage == null || discountPercentage == 0) {
            return BigDecimal.ZERO;
        }
        return originalPrice.subtract(finalPrice);
    }
    
    public Integer getTotalStarCount() {
        return starCount + (bonusStars != null ? bonusStars : 0);
    }
    
    public String getFormattedPrice() {
        return String.format("%.2f %s", finalPrice, currency);
    }
    
    public String getFormattedOriginalPrice() {
        return String.format("%.2f %s", originalPrice, currency);
    }
    
    public boolean hasDiscount() {
        return discountPercentage != null && discountPercentage > 0;
    }
    
    public boolean isAvailable() {
        if (!isEnabled) return false;
        
        LocalDateTime now = LocalDateTime.now();
        
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        
        if (validUntil != null && now.isAfter(validUntil)) {
            return false;
        }
        
        return true;
    }
    
    public boolean isAvailableForUser(int userOrderCount) {
        if (!isAvailable()) return false;
        
        if (minOrderCount != null && userOrderCount < minOrderCount) {
            return false;
        }
        
        return true;
    }
    
    public void recordSale(BigDecimal amount) {
        this.totalSold++;
        this.revenueGenerated = this.revenueGenerated.add(amount);
    }
    
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        
        if (emoji != null && !emoji.isEmpty()) {
            sb.append(emoji).append(" ");
        }
        
        sb.append(displayName);
        
        if (hasDiscount()) {
            sb.append(" (").append(discountPercentage).append("% скидка)");
        }
        
        if (isPopular) {
            sb.append(" ⭐ Популярный");
        }
        
        if (bonusStars != null && bonusStars > 0) {
            sb.append(" + ").append(bonusStars).append(" бонус");
        }
        
        return sb.toString();
    }
    
    public String getStatusEmoji() {
        if (!isEnabled) return "❌";
        if (!isAvailable()) return "⏰";
        if (isPopular) return "🌟";
        if (hasDiscount()) return "🏷️";
        return "✅";
    }
    
    public String getTypeEmoji() {
        return switch (packageType) {
            case STANDARD -> "📦";
            case PREMIUM -> "💎";
            case LIMITED_TIME -> "⏰";
            case VIP -> "👑";
            case PROMOTIONAL -> "🎉";
        };
    }
    
    public BigDecimal getPricePerStar() {
        if (starCount == 0) return BigDecimal.ZERO;
        return finalPrice.divide(BigDecimal.valueOf(starCount), 4, BigDecimal.ROUND_HALF_UP);
    }
}
