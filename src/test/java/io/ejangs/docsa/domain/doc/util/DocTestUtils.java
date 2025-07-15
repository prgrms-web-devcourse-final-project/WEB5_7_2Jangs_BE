package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

public class DocTestUtils {

    public static List<Doc> createDocumentList(int count, User user) {
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

            // save는 i가 1일 때만 생성하고, 2일 때는 생략
            if (i == 1) {
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

    public static User createUser() {
        return User.builder()
                .email("test@test.com")
                .name("배문성")
                .password("q1w2e3r4!")
                .build();

    }

}
