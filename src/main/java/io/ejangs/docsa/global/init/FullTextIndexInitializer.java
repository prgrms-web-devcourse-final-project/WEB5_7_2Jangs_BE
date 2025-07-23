package io.ejangs.docsa.global.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FullTextIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    private static final String CHECK_INDEX_EXIST_SQL = """
            SELECT COUNT(1)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'docs'
              AND index_name = 'idx_title_ngram'
            """;

    private static final String CREATE_INDEX_SQL = """
            CREATE FULLTEXT INDEX idx_title_ngram
            ON docs (title)
            WITH PARSER ngram
            """;

    @EventListener(ApplicationReadyEvent.class)
    public void initFullTextIndex() {
        try {
            Integer count = jdbcTemplate.queryForObject(CHECK_INDEX_EXIST_SQL, Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute(CREATE_INDEX_SQL);
                log.info("FULLTEXT INDEX CREATED");
            }
        } catch (Exception e) {
            log.warn("FULLTEXT 인덱스 생성 실패: {}", e.getMessage());
        }
    }
}