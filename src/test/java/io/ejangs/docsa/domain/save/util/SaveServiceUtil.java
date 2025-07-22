package io.ejangs.docsa.domain.save.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.entity.User;
import java.util.List;
import java.util.Map;

public class SaveServiceUtil {

    private static final List<Map<String, Object>> data = List.of(
            Map.of("text1", "Key features"),
            Map.of("text2", "Key features")
    );

    public static User createUser() {
        return User.builder()
                .email("email@gmail.com")
                .name("han")
                .password("password")
                .build();
    }

    public static Doc createDoc(User user) {
        return Doc.builder()
                .user(user)
                .title("title")
                .build();
    }

    public static Branch createBranch(Doc doc) {
        return Branch.builder()
                .doc(doc)
                .name("branch")
                .build();
    }

    public static Save createSave(Branch branch) {
        return Save.builder()
                .branch(branch)
                .build();
    }

    public static SaveContent createSaveContent() {
        return SaveContent.builder()
                .content(data)
                .build();
    }
}
