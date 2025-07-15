package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.branch.dto.BranchDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dto.CommitDto;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dto.EdgeDto;
import io.ejangs.docsa.domain.doc.entity.Edge;

public class GraphMapper {

    public static CommitDto toCommitDto(Commit commit) {
        return new CommitDto(
                commit.getId(),
                commit.getBranch().getId(),
                commit.getTitle(),
                commit.getDescription(),
                commit.getCreatedAt()
        );
    }

    public static EdgeDto toEdgeDto(Edge edge) {
        return new EdgeDto(
                edge.getPrevCommit().getId(),
                edge.getNextCommit().getId()
        );
    }

    public static BranchDto toBranchDto(Branch branch) {
        return new BranchDto(
                branch.getId(),
                branch.getName(),
                branch.getCreatedAt(),
                branch.getFromCommit() != null ? branch.getFromCommit().getId() : null,
                branch.getRootCommit() != null ? branch.getRootCommit().getId() : null,
                branch.getLeafCommit() != null ? branch.getLeafCommit().getId() : null,
                branch.getSave() != null ? branch.getSave().getId() : null
        );
    }
}
