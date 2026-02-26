package io.ejangs.docsa.domain.commit.util;

import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.edge.entity.Edge;
import io.ejangs.docsa.domain.user.entity.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

public class CommitMockTestUtils {

    public static User createUser() {
        User user = User.builder()
                .email("user@ejangs.io")
                .password("userPassword123")
                .name("user")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    public static BlockDto createBlockRequest(String blockId) {
        Map<String, Object> blockData = new HashMap<>();
        blockData.put("id", blockId);
        blockData.put("type", "text");
        blockData.put("content", "Test content");
        return new BlockDto(blockData);
    }

    public static Block createBlock(String blockId) {
        Map<String, Object> content = new HashMap<>();
        content.put("id", blockId);
        content.put("type", "text");
        content.put("content", "Test content");
        return Block.builder()
                .content(content)
                .build();
    }

    public static Doc createDoc(User user) {
        Doc doc = Doc.builder()
                .title("doc-title")
                .user(user)
                .build();
        ReflectionTestUtils.setField(doc, "id", 1L);
        return doc;
    }

    public static Branch createBranch(Doc doc, Commit baseCommit) {
        Branch branch = Branch.builder()
                .fromCommit(baseCommit)
                .doc(doc)
                .build();
        ReflectionTestUtils.setField(branch, "id", 1L);
        ReflectionTestUtils.setField(branch, "leafCommit", baseCommit);
        return branch;
    }

    public static Commit createBaseCommit(Branch branch) {
        Commit commit = Commit.builder()
                .title("Test Base Commit")
                .description("Test Base Description")
                .commitMongoId("base-mongo-commit-id")
                .branch(branch)
                .build();
        ReflectionTestUtils.setField(commit, "id", 1L);
        return commit;
    }

    public static Edge createEdge(Doc doc, Commit baseCommit, Commit savedCommit) {
        Edge edge = Edge.builder()
                .doc(doc)
                .prevCommit(baseCommit)
                .nextCommit(savedCommit)
                .build();
        ReflectionTestUtils.setField(edge, "id", 1L);
        return edge;
    }

    public static CommitBlockSequence createCommitBlockSequence() {
        CommitBlockSequence cbs = CommitBlockSequence.builder()
                .blockOrders(List.of("block1", "block2"))
                .build();
        ReflectionTestUtils.setField(cbs, "id", "base-mongo-commit-id");
        return cbs;
    }

    public static Commit createMockCommit(Branch branch, Long id) {
        Commit commit = Commit.builder()
                .title("Test commit message")
                .description("Test commit message")
                .commitMongoId("mongo-commit-id")
                .branch(branch)
                .build();
        ReflectionTestUtils.setField(commit, "id", id);
        return commit;
    }

    public static List<Map<String, Object>> createMockContent() {
        return List.of(
                Map.of("id", "block1", "type", "paragraph", "content", "Sample content 1"),
                Map.of("id", "block2", "type", "header", "content", "Sample header")
        );
    }
}
