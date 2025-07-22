package io.ejangs.docsa.domain.commit.util;

import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.user.entity.User;
import java.util.List;
import java.util.Map;

public class CommitIntegrationTestUtils {

    public static User createTestUser() {
        return User.builder()
                .email("test@example.com")
                .name("Test User")
                .password("password")
                .build();
    }

    public static Doc createTestDoc(User testUser) {
        return Doc.builder()
                .title("Test Document")
                .user(testUser)
                .build();
    }

    public static Branch createTestBranch(String name, Doc testDoc) {
        return Branch.builder()
                .name(name)
                .doc(testDoc)
                .build();
    }

    public static Commit createTestCommit(Branch branch, String title) {
        return Commit.builder()
                .title(title)
                .description("Test commit description")
                .branch(branch)
                .commitMongoId("test-mongo-id-" + System.currentTimeMillis())
                .build();
    }

    public static MergeCommitRequest createMergeCommitRequest(Branch baseBranch, Branch targetBranch) {
        return new MergeCommitRequest(
                "Merge commit",
                "Merge feature into main",
                baseBranch.getId(),
                targetBranch.getId(),
                createTestBlockContent()
        );
    }

    public static List<BlockDto> createTestBlockContent() {
        return List.of(
                new BlockDto(Map.of(
                        "id", "block-1",
                        "type", "paragraph",
                        "data", Map.of("text", "Test content 1")
                )),
                new BlockDto(Map.of(
                        "id", "block-2",
                        "type", "paragraph",
                        "data", Map.of("text", "Test content 2")
                ))
        );
    }
}
