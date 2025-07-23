package io.ejangs.docsa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ActiveProfiles;

@EnableAsync
@EnableRetry
@EnableScheduling
@SpringBootTest
@ActiveProfiles("test")
class DocsaApplicationTests {

    @Test
    void contextLoads() {
    }

}
