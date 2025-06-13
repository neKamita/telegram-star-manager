package shit.back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TelegramStarManagerApplication {

    public static void main(String[] args) {
        System.out.println("🚀 ДИАГНОСТИКА SSE: Starting TelegramStarManager with @EnableAsync and @EnableScheduling");
        SpringApplication.run(TelegramStarManagerApplication.class, args);
        System.out.println("✅ ДИАГНОСТИКА SSE: TelegramStarManager started successfully");
    }
}
