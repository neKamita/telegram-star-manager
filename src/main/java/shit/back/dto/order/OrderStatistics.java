package shit.back.dto.order;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderStatistics {
    private long totalOrders;
    private long completedOrders;
    private long pendingOrders;
    private long failedOrders;
    private BigDecimal totalRevenue;
    private BigDecimal todayRevenue;
    private BigDecimal monthRevenue;
    private BigDecimal averageOrderValue;
    private Double conversionRate;
}