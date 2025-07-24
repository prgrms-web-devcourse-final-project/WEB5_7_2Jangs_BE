package io.ejangs.docsa.domain.commit.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
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

    public static User createInvalidTestUser() {
        return User.builder()
                .email("invalid@example.com")
                .name("Invalid User")
                .password("1234")
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

    public static Branch createTestBranch(String name, Doc testDoc, Commit from) {
        return Branch.builder()
                .name(name)
                .doc(testDoc)
                .fromCommit(from)
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

    public static MergeCommitRequest createMergeCommitRequest(Branch baseBranch,
            Branch targetBranch) {
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

    public static Edge createTestEdge(Doc doc, Commit prev, Commit next) {
        return Edge.builder()
                .doc(doc)
                .prevCommit(prev)
                .nextCommit(next)
                .build();
    }

    public static TestDocIntegrationDto createDocumentForIntegrationTest(User user,
            CommitBlockSequenceRepository commitBlockSequenceRepository,
            BlockRepository blockRepository) throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> parsedJson10 =
                mapper.readValue(editorJson10, new TypeReference<>() {
                });
        List<Map<String, Object>> parsedJson20 =
                mapper.readValue(editorJson20, new TypeReference<>() {
                });
        List<Map<String, Object>> parsedJson21 =
                mapper.readValue(editorJson21, new TypeReference<>() {
                });
        List<Map<String, Object>> parsedJson22 =
                mapper.readValue(editorJson22, new TypeReference<>() {
                });
        List<Map<String, Object>> parsedJson30 =
                mapper.readValue(editorJson30, new TypeReference<>() {
                });

        // --- 블록 등록 ---
        List<Block> blocks10 = blockRepository.saveAll(
                parsedJson10.stream()
                        .map(b -> Block.builder().content(b).build())
                        .toList());
        List<Block> blocks20 = blockRepository.saveAll(
                parsedJson20.stream()
                        .map(b -> Block.builder().content(b).build())
                        .toList());
        List<Block> blocks21 = blockRepository.saveAll(
                parsedJson21.stream()
                        .map(b -> Block.builder().content(b).build())
                        .toList());
        List<Block> blocks22 = blockRepository.saveAll(
                parsedJson22.stream()
                        .map(b -> Block.builder().content(b).build())
                        .toList());
        List<Block> blocks30 = blockRepository.saveAll(
                parsedJson30.stream()
                        .map(b -> Block.builder().content(b).build())
                        .toList());

        // 문서 1: 저장 없음, 커밋 여러 개
        Doc doc1 = Doc.builder().title("문서 1").user(user).build();

        CommitBlockSequence commitSeq10 = commitBlockSequenceRepository.save(
                CommitBlockSequence.builder()
                        .blockOrders(blocks10.stream().map(Block::getId).toList())
                        .build());
        CommitBlockSequence commitSeq20 = commitBlockSequenceRepository.save(
                CommitBlockSequence.builder()
                        .blockOrders(blocks20.stream().map(Block::getId).toList())
                        .build());
        CommitBlockSequence commitSeq21 = commitBlockSequenceRepository.save(
                CommitBlockSequence.builder()
                        .blockOrders(blocks21.stream().map(Block::getId).toList())
                        .build());
        CommitBlockSequence commitSeq22 = commitBlockSequenceRepository.save(
                CommitBlockSequence.builder()
                        .blockOrders(blocks22.stream().map(Block::getId).toList())
                        .build());
        CommitBlockSequence commitSeq30 = commitBlockSequenceRepository.save(
                CommitBlockSequence.builder()
                        .blockOrders(blocks30.stream().map(Block::getId).toList())
                        .build());

        // 브랜치 1의 커밋 설정
        Branch branch1 = Branch.builder().name("브랜치 1").doc(doc1).build();

        Commit commit10 = Commit.builder()
                .title("커밋 1").description("desc1")
                .commitMongoId(commitSeq10.getId())
                .branch(branch1)
                .build();
        Commit commit20 = Commit.builder()
                .title("커밋 2").description("desc2")
                .commitMongoId(commitSeq20.getId())
                .branch(branch1)
                .build();
        Commit commit30 = Commit.builder()
                .title("커밋 3").description("desc3")
                .commitMongoId(commitSeq30.getId())
                .branch(branch1)
                .build();

        // 브랜치 2 커밋 설정
        Branch branch2 = Branch.builder().name("브랜치 2").doc(doc1).fromCommit(commit20).build();

        Commit commit21 = Commit.builder()
                .title("커밋 A").description("descA")
                .commitMongoId(commitSeq21.getId())
                .branch(branch2)
                .build();
        Commit commit22 = Commit.builder()
                .title("커밋 B").description("descB")
                .commitMongoId(commitSeq22.getId())
                .branch(branch2)
                .build();

        // 브랜치 1 초기화
        branch1.initializeRootCommitIfNull(commit10);
        branch1.updateLeafCommit(commit30);

        // 간선 설정
        Edge.builder().doc(doc1).prevCommit(commit10).nextCommit(commit20).build();
        Edge.builder().doc(doc1).prevCommit(commit20).nextCommit(commit30).build();

        // 브랜치 2 초기화
        branch2.initializeRootCommitIfNull(commit21);
        branch2.updateLeafCommit(commit22);

        // 간선 설정
        Edge.builder().doc(doc1).prevCommit(commit20).nextCommit(commit21).build();
        Edge.builder().doc(doc1).prevCommit(commit21).nextCommit(commit22).build();
        Edge.builder().doc(doc1).prevCommit(commit22).nextCommit(commit30).build();

        return new TestDocIntegrationDto(doc1, branch1, branch2, commit10, commit20, commit21,
                commit22, commit30);
    }

    private static final String editorJson10 = """
            [
              { "id": "a1", "type": "paragraph", "data": {"text": "문단 1: 어찌라구저찌라구"} },
              { "id": "a2", "type": "paragraph", "data": {"text": "문단 2: 뷀OTL뷁입니다."} },
              { "id": "a3", "type": "paragraph", "data": {"text": "문단 3: 무지개반사 삐융빠슝"} }
            ]
            """;

    private static final String editorJson20 = """
            [
              { "id": "a2", "type": "paragraph", "data": {"text": "문단 2: 가나다라마바사."} }
            ]
            """;

    private static final String editorJson21 = """
            [
              { "id": "a1", "type": "paragraph", "data": {"text": "문단 1: 1234567"} },
              { "id": "a4", "type": "paragraph", "data": {"text": "문단 4: 삐융빠슝 삐융빠슝"} }
            ]
            """;

    private static final String editorJson22 = """
            [
              { "id": "a2", "type": "paragraph", "data": {"text": "문단 2: 아자차카타파하."} },
              { "id": "a4", "type": "paragraph", "data": {"text": "문단 4: 하늘은 무슨색이더라"} }
            ]
            """;

    private static final String editorJson30 = """
            [
              { "id": "b1", "type": "paragraph", "data": {"text": "문단 1: 이것은 어쩌고 저쩌고에 대한 문단이올씨다."} },
              { "id": "b2", "type": "paragraph", "data": {"text": "문단 2: 테스트가 너무 좋다는 내용에 대한 문단"} },
              { "id": "b3", "type": "paragraph", "data": {"text": "문단 3: 테스트 코드가 너무 싫어서 미치겠다는 문단"} },
              { "id": "b4", "type": "paragraph", "data": {"text": "문단 4: 하체하기싫다는 문단"} },
              { "id": "b5", "type": "paragraph", "data": {"text": "문단 5: 몰라어쩌구저꺼궁롱ㄹ라알이;ㅇㄹ"} }
            ]
            """;
}
