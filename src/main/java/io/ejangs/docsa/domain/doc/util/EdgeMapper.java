package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;

public class EdgeMapper {

    public static Edge toEntity(Doc doc, Commit prev, Commit next) {
        return Edge.builder()
                .doc(doc)
                .prevCommit(prev)
                .nextCommit(next)
                .build();
    }
}
