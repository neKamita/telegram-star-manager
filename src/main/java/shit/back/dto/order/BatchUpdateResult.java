package shit.back.dto.order;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BatchUpdateResult {
    private int successCount;
    private int failureCount;
    private List<String> successfulOrderIds;
    private List<String> failureReasons;
}