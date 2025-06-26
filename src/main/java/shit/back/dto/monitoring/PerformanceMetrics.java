package shit.back.dto.monitoring;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    // Основные поля производительности
    private Double cpuUsage;
    private Double memoryUsage;
    private Double responseTime;
    private Long requestCount;
    private Long errorCount;
    private Long uptime;

    // Поля для совместимости с JavaScript
    private Double averageResponseTime;
    private Integer memoryUsagePercent;
    private Integer cacheHitRatio;

    // Database & Cache метрики - НОВЫЕ ПОЛЯ
    private Integer dbPoolUsage;
    private Integer cacheMissRatio;
    private Integer activeDbConnections;

    // Дополнительные поля для полной функциональности
    private Long totalUsers;
    private Long activeUsers;
    private Long onlineUsers;
    private Long totalOrders;
    private Integer healthScore;
    private LocalDateTime timestamp;
    private String source;
    private Long collectionNumber;
    private Map<String, Object> metadata;

}
