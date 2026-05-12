package io.ejangs.docsa.domain.doc.readmodel.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("backfill")
@RequiredArgsConstructor
public class DocListReadModelBackfillJob implements ApplicationRunner {

    private final DocListReadModelBackfillService backfillService;
    private final ConfigurableApplicationContext context;

    @Value("${backfill.doc-list.batch-size:1000}")
    private int batchSize;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[DocListReadModelBackfillJob] started. batchSize={}", batchSize);

        int inserted = backfillService.backfillAll(batchSize);

        log.info("[DocListReadModelBackfillJob] completed. inserted={}", inserted);
        SpringApplication.exit(context, () -> 0);
    }
}
