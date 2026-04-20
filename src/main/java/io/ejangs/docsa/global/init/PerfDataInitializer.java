package io.ejangs.docsa.global.init;

import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.save.document.SaveContent;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"local", "stg"})
public class PerfDataInitializer implements ApplicationRunner {

    private static final String PASSWORD = "Testtest1";

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${perf.seed.run-id:perf-local}")
    private String runId;

    @Value("${perf.seed.user-prefix:perfuser}")
    private String userPrefix;

    @Value("${perf.seed.user-domain:test.com}")
    private String userDomain;

    @Value("${perf.seed.user-count:0}")
    private int userCount;

    @Value("${perf.seed.docs-per-user:20}")
    private int docsPerUser;

    @Value("${perf.seed.branches-per-doc:1}")
    private int branchesPerDoc;

    @Value("${perf.seed.commits-per-branch:3}")
    private int commitsPerBranch;

    @Value("${perf.seed.blocks-per-save:50}")
    private int blocksPerSave;

    @Value("${perf.seed.blocks-per-commit:50}")
    private int blocksPerCommit;

    @Value("${perf.seed.clean-before:true}")
    private boolean cleanBefore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        validateOptions();
        if (userCount == 0) {
            return;
        }

        long started = System.currentTimeMillis();
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        String titlePrefix = titlePrefix();

        if (cleanBefore) {
            cleanExisting(titlePrefix);
        }

        for (int userNo = 1; userNo <= userCount; userNo++) {
            Long userId = createUser(userNo, encodedPassword);
            for (int docNo = 1; docNo <= docsPerUser; docNo++) {
                createDocGraph(userId, userNo, docNo);
            }
        }

        long elapsed = System.currentTimeMillis() - started;
        log.info(
                "[PERF_SEED] done runId={}, users={}, docsPerUser={}, branchesPerDoc={}, commitsPerBranch={}, blocksPerSave={}, blocksPerCommit={}, elapsedMs={}",
                runId, userCount, docsPerUser, branchesPerDoc, commitsPerBranch, blocksPerSave, blocksPerCommit, elapsed
        );
    }

    private void validateOptions() {
        if (userCount < 0 || docsPerUser < 0 || branchesPerDoc < 1
                || commitsPerBranch < 0 || blocksPerSave < 1 || blocksPerCommit < 1) {
            throw new IllegalArgumentException("perf seed counts must be valid");
        }
    }

    private void cleanExisting(String titlePrefix) {
        List<Long> docIds = jdbcTemplate.queryForList(
                "SELECT id FROM docs WHERE title LIKE ?",
                Long.class,
                titlePrefix + "%"
        );

        if (!docIds.isEmpty()) {
            List<Long> branchIds = selectIdsByIn(
                    "SELECT id FROM branches WHERE document_id IN (%s)",
                    docIds
            );
            List<Long> commitIds = selectIdsByIn(
                    "SELECT id FROM commits WHERE branch_id IN (%s)",
                    branchIds
            );
            deleteByIn("DELETE FROM saves WHERE branch_id IN (%s)", branchIds);
            deleteByIn("DELETE FROM edges WHERE document_id IN (%s)", docIds);
            updateByIn("""
                    UPDATE branches
                    SET from_commit_id = NULL,
                        root_commit_id = NULL,
                        leaf_commit_id = NULL,
                        merge_target_commit_id = NULL
                    WHERE id IN (%s)
                    """, branchIds);
            deleteByIn("DELETE FROM commits WHERE id IN (%s)", commitIds);
            deleteByIn("DELETE FROM branches WHERE id IN (%s)", branchIds);
            deleteByIn("DELETE FROM docs WHERE id IN (%s)", docIds);
        }

        mongoTemplate.getCollection(mongoTemplate.getCollectionName(SaveContent.class))
                .deleteMany(new Document());
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(CommitBlockSequence.class))
                .deleteMany(new Document());
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(Block.class))
                .deleteMany(new Document());
    }

    private Long createUser(int userNo, String encodedPassword) {
        String email = userEmail(userNo);
        List<Long> existingUserIds = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                email
        );
        if (!existingUserIds.isEmpty()) {
            return existingUserIds.getFirst();
        }

        return insertAndReturnId(
                "INSERT INTO users (created_at, updated_at, email, name, password) VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    LocalDateTime now = LocalDateTime.now();
                    statement.setObject(1, now);
                    statement.setObject(2, now);
                    statement.setString(3, email);
                    statement.setString(4, "%s_u%03d".formatted(userPrefix, userNo));
                    statement.setString(5, encodedPassword);
                }
        );
    }

    private void createDocGraph(Long userId, int userNo, int docNo) {
        LocalDateTime baseTime = LocalDateTime.now()
                .minusDays(docsPerUser - docNo)
                .plusSeconds(userNo);

        Long docId = insertAndReturnId(
                "INSERT INTO docs (created_at, updated_at, user_id, title) VALUES (?, ?, ?, ?)",
                statement -> {
                    statement.setObject(1, baseTime);
                    statement.setObject(2, baseTime.plusMinutes(branchesPerDoc));
                    statement.setLong(3, userId);
                    statement.setString(4, docTitle(userNo, docNo));
                }
        );

        DocSeedBuffer seedBuffer = new DocSeedBuffer();
        List<Long> mainCommitIds = createBranchGraph(
                docId, userNo, docNo, 1, null, baseTime.plusMinutes(1), seedBuffer
        );

        for (int branchNo = 2; branchNo <= branchesPerDoc; branchNo++) {
            Long fromCommitId = selectBranchBaseCommit(mainCommitIds, branchNo);
            createBranchGraph(docId, userNo, docNo, branchNo, fromCommitId, baseTime.plusMinutes(branchNo), seedBuffer);
        }
        flushDocSeedBuffer(seedBuffer);
    }

    private Long selectBranchBaseCommit(List<Long> mainCommitIds, int branchNo) {
        if (mainCommitIds.isEmpty()) {
            return null;
        }
        int index = Math.min(branchNo - 2, mainCommitIds.size() - 1);
        return mainCommitIds.get(index);
    }

    private List<Long> createBranchGraph(
            Long docId,
            int userNo,
            int docNo,
            int branchNo,
            Long fromCommitId,
            LocalDateTime branchTime,
            DocSeedBuffer seedBuffer
    ) {
        Long branchId = insertBranch(docId, branchNo, fromCommitId, branchTime);
        List<Long> commitIds = createCommits(
                docId, branchId, fromCommitId, userNo, docNo, branchNo, branchTime, seedBuffer
        );
        if (!commitIds.isEmpty()) {
            updateBranchCommitRefs(branchId, commitIds.getFirst(), commitIds.getLast());
        }

        String saveMongoId = saveMongoId(userNo, docNo, branchNo);
        addSave(seedBuffer, branchId, saveMongoId, branchTime.plusSeconds(commitsPerBranch + 1L));
        addSaveContent(seedBuffer, saveMongoId, userNo, docNo, branchNo);
        return commitIds;
    }

    private Long insertBranch(Long docId, int branchNo, Long fromCommitId, LocalDateTime branchTime) {
        return insertAndReturnId(
                """
                INSERT INTO branches
                    (created_at, updated_at, document_id, from_commit_id, leaf_commit_id,
                     merge_target_commit_id, root_commit_id, name)
                VALUES (?, ?, ?, ?, NULL, NULL, NULL, ?)
                """,
                statement -> {
                    statement.setObject(1, branchTime);
                    statement.setObject(2, branchTime);
                    statement.setLong(3, docId);
                    statement.setObject(4, fromCommitId);
                    statement.setString(5, branchName(branchNo));
                }
        );
    }

    private List<Long> createCommits(
            Long docId,
            Long branchId,
            Long fromCommitId,
            int userNo,
            int docNo,
            int branchNo,
            LocalDateTime branchTime,
            DocSeedBuffer seedBuffer
    ) {
        List<Long> commitIds = new ArrayList<>(commitsPerBranch);
        Long prevCommitId = fromCommitId;
        for (int commitNo = 1; commitNo <= commitsPerBranch; commitNo++) {
            String commitMongoId = commitMongoId(userNo, docNo, branchNo, commitNo);
            addCommitContent(seedBuffer, commitMongoId, userNo, docNo, branchNo, commitNo);
            LocalDateTime commitTime = branchTime.plusSeconds(commitNo);
            Long commitId = insertCommit(branchId, commitMongoId, userNo, docNo, branchNo, commitNo, commitTime);
            if (prevCommitId != null) {
                addEdge(seedBuffer, docId, prevCommitId, commitId);
            }
            commitIds.add(commitId);
            prevCommitId = commitId;
        }
        return commitIds;
    }

    private Long insertCommit(
            Long branchId,
            String commitMongoId,
            int userNo,
            int docNo,
            int branchNo,
            int commitNo,
            LocalDateTime timestamp
    ) {
        return insertAndReturnId(
                """
                INSERT INTO commits
                    (created_at, updated_at, commit_mongo_id, description, title, branch_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setObject(1, timestamp);
                    statement.setObject(2, timestamp);
                    statement.setString(3, commitMongoId);
                    statement.setString(4, "perf seed commit u%03d d%03d b%02d c%03d"
                            .formatted(userNo, docNo, branchNo, commitNo));
                    statement.setString(5, "Perf commit %03d".formatted(commitNo));
                    statement.setLong(6, branchId);
                }
        );
    }

    private void addEdge(DocSeedBuffer seedBuffer, Long docId, Long prevCommitId, Long nextCommitId) {
        seedBuffer.edgeRows.add(new EdgeRow(docId, prevCommitId, nextCommitId));
    }

    private void updateBranchCommitRefs(Long branchId, Long rootCommitId, Long leafCommitId) {
        jdbcTemplate.update(
                "UPDATE branches SET root_commit_id = ?, leaf_commit_id = ? WHERE id = ?",
                rootCommitId,
                leafCommitId,
                branchId
        );
    }

    private void addSave(DocSeedBuffer seedBuffer, Long branchId, String saveMongoId, LocalDateTime timestamp) {
        seedBuffer.saveRows.add(new SaveRow(timestamp, branchId, saveMongoId));
    }

    private void addSaveContent(DocSeedBuffer seedBuffer, String saveMongoId, int userNo, int docNo, int branchNo) {
        seedBuffer.saveContentDocuments.add(new Document("_id", saveMongoId)
                .append("content", buildBlocks(userNo, docNo, branchNo)));
    }

    private void addCommitContent(
            DocSeedBuffer seedBuffer,
            String commitMongoId,
            int userNo,
            int docNo,
            int branchNo,
            int commitNo
    ) {
        List<String> blockMongoIds = new ArrayList<>(blocksPerCommit);

        for (int blockNo = 1; blockNo <= blocksPerCommit; blockNo++) {
            String blockMongoId = blockMongoId(userNo, docNo, branchNo, commitNo, blockNo);
            blockMongoIds.add(blockMongoId);
            seedBuffer.blockDocuments.add(new Document("_id", blockMongoId)
                    .append("content", commitBlockContent(userNo, docNo, branchNo, commitNo, blockNo)));
        }

        seedBuffer.commitBlockSequenceDocuments.add(new Document("_id", commitMongoId)
                .append("blockOrders", blockMongoIds));
    }

    private void flushDocSeedBuffer(DocSeedBuffer seedBuffer) {
        flushSaveRows(seedBuffer.saveRows);
        flushEdgeRows(seedBuffer.edgeRows);
        if (!seedBuffer.blockDocuments.isEmpty()) {
            mongoTemplate.getCollection(mongoTemplate.getCollectionName(Block.class))
                    .insertMany(seedBuffer.blockDocuments);
        }
        if (!seedBuffer.commitBlockSequenceDocuments.isEmpty()) {
            mongoTemplate.getCollection(mongoTemplate.getCollectionName(CommitBlockSequence.class))
                    .insertMany(seedBuffer.commitBlockSequenceDocuments);
        }
        if (!seedBuffer.saveContentDocuments.isEmpty()) {
            mongoTemplate.getCollection(mongoTemplate.getCollectionName(SaveContent.class))
                    .insertMany(seedBuffer.saveContentDocuments);
        }
    }

    private void flushSaveRows(List<SaveRow> saveRows) {
        if (saveRows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO saves (created_at, updated_at, branch_id, save_mongo_id) VALUES (?, ?, ?, ?)",
                saveRows,
                saveRows.size(),
                (statement, row) -> {
                    statement.setObject(1, row.timestamp());
                    statement.setObject(2, row.timestamp());
                    statement.setLong(3, row.branchId());
                    statement.setString(4, row.saveMongoId());
                }
        );
    }

    private void flushEdgeRows(List<EdgeRow> edgeRows) {
        if (edgeRows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO edges (document_id, prev_commit_id, next_commit_id) VALUES (?, ?, ?)",
                edgeRows,
                edgeRows.size(),
                (statement, row) -> {
                    statement.setLong(1, row.docId());
                    statement.setLong(2, row.prevCommitId());
                    statement.setLong(3, row.nextCommitId());
                }
        );
    }

    private Map<String, Object> commitBlockContent(int userNo, int docNo, int branchNo, int commitNo, int blockNo) {
        return Map.of(
                "id", "editor-%s-u%03d-d%03d-b%02d-c%03d-block%04d"
                        .formatted(runId, userNo, docNo, branchNo, commitNo, blockNo),
                "type", "paragraph",
                "data", Map.of(
                        "text", "PERF-%s u%03d d%03d branch%02d commit%03d paragraph %04d"
                                .formatted(runId, userNo, docNo, branchNo, commitNo, blockNo)
                )
        );
    }

    private List<Map<String, Object>> buildBlocks(int userNo, int docNo, int branchNo) {
        List<Map<String, Object>> blocks = new ArrayList<>(blocksPerSave);
        for (int blockNo = 1; blockNo <= blocksPerSave; blockNo++) {
            String blockId = "%s-u%03d-d%03d-b%02d-block%04d"
                    .formatted(runId, userNo, docNo, branchNo, blockNo);
            blocks.add(Map.of(
                            "id", blockId,
                            "type", "paragraph",
                            "data", Map.of(
                            "text", "PERF-%s u%03d d%03d branch%02d paragraph %04d"
                                    .formatted(runId, userNo, docNo, branchNo, blockNo)
                    )
            ));
        }
        return blocks;
    }

    private Long insertAndReturnId(String sql, StatementBinder binder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            binder.bind(statement);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to read generated key");
        }
        return key.longValue();
    }

    private List<Long> selectIdsByIn(String sqlTemplate, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(ids.size());
        return jdbcTemplate.queryForList(sqlTemplate.formatted(placeholders), Long.class, ids.toArray());
    }

    private void deleteByIn(String sqlTemplate, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = placeholders(ids.size());
        jdbcTemplate.update(sqlTemplate.formatted(placeholders), ids.toArray());
    }

    private void updateByIn(String sqlTemplate, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = placeholders(ids.size());
        jdbcTemplate.update(sqlTemplate.formatted(placeholders), ids.toArray());
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private String titlePrefix() {
        return "PERF-" + runId + "-";
    }

    private String saveMongoPrefix() {
        return "perf-" + runId + "-";
    }

    private String mongoPrefix() {
        return "perf-" + runId + "-";
    }

    private String userEmail(int userNo) {
        return "%s_u%03d@%s".formatted(userPrefix, userNo, userDomain);
    }

    private String docTitle(int userNo, int docNo) {
        return "%su%03d-d%03d".formatted(titlePrefix(), userNo, docNo);
    }

    private String branchName(int branchNo) {
        return branchNo == 1 ? "main" : "branch-%02d".formatted(branchNo);
    }

    private String saveMongoId(int userNo, int docNo, int branchNo) {
        return "%su%03d-d%03d-b%02d-save".formatted(saveMongoPrefix(), userNo, docNo, branchNo);
    }

    private String commitMongoId(int userNo, int docNo, int branchNo, int commitNo) {
        return "%su%03d-d%03d-b%02d-c%03d-cbs".formatted(mongoPrefix(), userNo, docNo, branchNo, commitNo);
    }

    private String blockMongoId(int userNo, int docNo, int branchNo, int commitNo, int blockNo) {
        return "%su%03d-d%03d-b%02d-c%03d-block%04d"
                .formatted(mongoPrefix(), userNo, docNo, branchNo, commitNo, blockNo);
    }

    private record SaveRow(LocalDateTime timestamp, Long branchId, String saveMongoId) {
    }

    private record EdgeRow(Long docId, Long prevCommitId, Long nextCommitId) {
    }

    private static class DocSeedBuffer {

        private final List<Document> blockDocuments = new ArrayList<>();
        private final List<Document> commitBlockSequenceDocuments = new ArrayList<>();
        private final List<Document> saveContentDocuments = new ArrayList<>();
        private final List<SaveRow> saveRows = new ArrayList<>();
        private final List<EdgeRow> edgeRows = new ArrayList<>();
    }

    @FunctionalInterface
    private interface StatementBinder {

        void bind(PreparedStatement statement) throws java.sql.SQLException;
    }
}
