package io.ejangs.docsa.domain.doc.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

public class DocTestUtils {

    public static List<Doc> createDocList(int count, User user) {
        List<Doc> docs = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            Doc doc = Doc.builder()
                    .title("테스트 문서 " + i)
                    .user(user)
                    .build();

            Branch branch = Branch.builder()
                    .name("테스트 브랜치" + i)
                    .doc(doc)
                    .build();

            doc.addBranch(branch);
            docs.add(doc);
        }
        return docs;
    }

    public static List<Doc> createDocumentListForUnitTest(int count, User user) {
        List<Doc> docs = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2025, 7, 16, 1, 0);

        for (int i = 1; i <= count; i++) {
            LocalDateTime time = baseTime.plusHours(i);

            Doc doc = Doc.builder()
                    .title("테스트 문서 " + i)
                    .user(user)
                    .build();
            ReflectionTestUtils.setField(doc, "id", (long) i);
            ReflectionTestUtils.setField(doc, "createdAt", time);
            ReflectionTestUtils.setField(doc, "updatedAt", time);

            Branch branch = Branch.builder()
                    .name("브랜치 " + i)
                    .doc(doc)
                    .build();
            ReflectionTestUtils.setField(branch, "id", (long) i);
            ReflectionTestUtils.setField(branch, "createdAt", time.plusMinutes(1));
            ReflectionTestUtils.setField(branch, "updatedAt", time.plusMinutes(1));

            Commit commit = Commit.builder()
                    .commitMongoId("mock-mongo-id-" + i)
                    .title("커밋 " + i)
                    .description("설명 " + i)
                    .branch(branch)
                    .build();
            ReflectionTestUtils.setField(commit, "id", i * 100L);
            ReflectionTestUtils.setField(commit, "createdAt", time.plusMinutes(2));
            ReflectionTestUtils.setField(commit, "updatedAt", time.plusMinutes(2));

            if (i % 2 == 1) {
                Save save = Save.builder()
                        .branch(branch)
                        .build();
                ReflectionTestUtils.setField(save, "id", i * 10L);
                ReflectionTestUtils.setField(save, "createdAt", time.plusMinutes(3));
                ReflectionTestUtils.setField(save, "updatedAt", time.plusMinutes(3));
                branch.setSave(save);
            }

            branch.addCommit(commit);
            branch.updateLeafCommit(commit);
            doc.addBranch(branch);

            docs.add(doc);
        }

        return docs;
    }

    public static List<Doc> createDocumentListForIntegrationTest(User user,
            SaveContentRepository saveContentRepository,
            CommitBlockSequenceRepository commitBlockSequenceRepository,
            BlockRepository blockRepository) throws JsonProcessingException {

        List<Doc> docs = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> parsedJson1 = mapper.readValue(editorJson1,
                new TypeReference<>() {
                });
        List<Map<String, Object>> parsedJson2 = mapper.readValue(editorJson2,
                new TypeReference<>() {
                });

        // --- 공통 블록 등록 ---
        Block block1 = blockRepository.save(
                Block.builder().content(parsedJson1.get(2)).build());
        Block block2 = blockRepository.save(
                Block.builder().content(parsedJson2.get(4)).build());

        // 문서 1: 저장 없음, 커밋 여러 개
        Doc doc1 = Doc.builder().title("문서 1").user(user).build();
        Branch branch1 = Branch.builder().name("브랜치 1").doc(doc1).build();

        CommitBlockSequence commitSeq1 = commitBlockSequenceRepository.save(
                CommitBlockSequence.builder().blockOrders(List.of(block1.getId())).build());
        CommitBlockSequence commitSeq2 = commitBlockSequenceRepository.save(
                CommitBlockSequence.builder().blockOrders(List.of(block2.getId())).build());

        Commit commit1 = Commit.builder()
                .title("커밋 1").description("desc")
                .commitMongoId(commitSeq1.getId())
                .branch(branch1)
                .build();
        Commit commit2 = Commit.builder()
                .title("커밋 2").description("desc")
                .commitMongoId(commitSeq2.getId())
                .branch(branch1)
                .build();

        branch1.updateLeafCommit(commit2);
        doc1.addBranch(branch1);
        docs.add(doc1);

        // 문서 2: 브랜치 2개, 각 브랜치에 저장 있음
        Doc doc2 = Doc.builder().title("문서 2").user(user).build();

        for (int i = 1; i <= 2; i++) {
            Branch branch = Branch.builder().name("브랜치 2-" + i).doc(doc2).build();

            CommitBlockSequence commitSeq = commitBlockSequenceRepository.save(
                    CommitBlockSequence.builder()
                            .blockOrders(List.of(block1.getId(), block2.getId()))
                            .build());

            Commit commit = Commit.builder()
                    .title("브랜치2-커밋 " + i)
                    .description("desc")
                    .branch(branch)
                    .commitMongoId(commitSeq.getId())
                    .build();

            Map<String, Object> saveJson = (i % 2 == 0) ? parsedJson2.get(2) : parsedJson1.get(2);
            SaveContent saveContent = SaveContent.builder()
                    .content(List.of(saveJson))
                    .build();
            saveContent = saveContentRepository.save(saveContent);

            Save save = Save.builder().branch(branch).build();
            save.updateSaveMongoId(saveContent.getId());

            branch.setSave(save);
            branch.updateLeafCommit(commit);
            doc2.addBranch(branch);
        }

        docs.add(doc2);
        return docs;
    }

    private static final String editorJson1 = """
            [
              { "id": "a1", "type": "paragraph", "data": {"text": "문단 1: 어찌라구저찌라구"} },
              { "id": "a2", "type": "paragraph", "data": {"text": "문단 2: 뷀OTL뷁입니다."} },
              { "id": "a3", "type": "paragraph", "data": {"text": "문단 3: 무지개반사 삐융빠슝"} }
            ]
            """;

    private static final String editorJson2 = """
            [
              { "id": "b1", "type": "paragraph", "data": {"text": "문단 1: 이것은 어쩌고 저쩌고에 대한 문단이올씨다."} },
              { "id": "b2", "type": "paragraph", "data": {"text": "문단 2: 테스트가 너무 좋다는 내용에 대한 문단"} },
              { "id": "b3", "type": "paragraph", "data": {"text": "문단 3: 테스트 코드가 너무 싫어서 미치겠다는 문단"} },
              { "id": "b4", "type": "paragraph", "data": {"text": "문단 4: 하체하기싫다는 문단"} },
              { "id": "b5", "type": "paragraph", "data": {"text": "문단 5: 몰라어쩌구저꺼궁롱ㄹ라알이;ㅇㄹ"} }
            ]
            """;

    public static User createUser() {
        return User.builder()
                .email("test@test.com")
                .name("배문성")
                .password("q1w2e3r4!")
                .build();

    }

}
