package shit.back.dto.order;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CustomerStats {
    private Long userId;
    private String username;
    private Long orderCount;
    private BigDecimal totalSpent;
}