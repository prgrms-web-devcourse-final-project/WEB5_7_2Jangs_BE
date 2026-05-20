package io.ejangs.docsa.global.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String OUTBOX_WAKE_UP_EXECUTOR = "outboxWakeUpExecutor";

    @Bean(name = OUTBOX_WAKE_UP_EXECUTOR)
    public Executor outboxWakeUpExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("outbox-wakeup-");
        executor.initialize();
        return executor;
    }
}
