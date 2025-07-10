package io.ejangs.docsa.domain.document.util;

import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.user.entity.User;
import java.util.ArrayList;
import java.util.List;

public class DocumentTestUtils {

    public static List<Document> createDocumentList(int count, User user) {
        List<Document> documents = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            documents.add(Document.builder()
                    .title("테스트 문서 " + i)
                    .user(user)
                    .build());
        }

        return documents;
    }

    public static User createUser() {
        return User.builder()
                .email("test@test.com")
                .name("배문성")
                .password("q1w2e3r4!")
                .build();

    }

}
