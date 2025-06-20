package shit.back.dto.order;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderMetrics {
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private long totalOrders;
    private long completedOrders;
    private long pendingOrders;
    private long failedOrders;
    private BigDecimal totalRevenue;
    private BigDecimal averageOrderValue;
    private double conversionRate;
}