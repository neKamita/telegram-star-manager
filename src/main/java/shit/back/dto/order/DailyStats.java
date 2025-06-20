package shit.back.dto.order;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@Builder
public class DailyStats {
    private Date date;
    private Long orderCount;
    private BigDecimal revenue;
}