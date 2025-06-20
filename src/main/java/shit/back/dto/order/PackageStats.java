package shit.back.dto.order;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PackageStats {
    private String packageName;
    private Long orderCount;
    private BigDecimal totalRevenue;
}