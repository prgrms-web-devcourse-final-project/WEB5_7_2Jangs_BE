package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.user.entity.User;
import java.util.ArrayList;
import java.util.List;

public class DocumentTestUtils {

    public static List<Doc> createDocumentList(int count, User user) {
        List<Doc> docs = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            docs.add(Doc.builder()
                    .title("테스트 문서 " + i)
                    .user(user)
                    .build());
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
