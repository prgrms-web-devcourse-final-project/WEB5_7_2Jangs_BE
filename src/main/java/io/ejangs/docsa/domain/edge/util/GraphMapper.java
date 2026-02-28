package io.ejangs.docsa.domain.edge.util;

import io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.edge.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.edge.dto.graph.EdgeDto;
import io.ejangs.docsa.domain.edge.dto.GraphResponse;
import io.ejangs.docsa.domain.edge.entity.Edge;

import java.util.List;

public class GraphMapper {

    public static CommitGraphDto toCommitDto(Commit commit) {
        return new CommitGraphDto(
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

    public static BranchGraphDto toBranchDto(Branch branch) {
        return new BranchGraphDto(
                branch.getId(),
                branch.getName(),
                branch.getCreatedAt(),
                branch.getFromCommit() != null ? branch.getFromCommit().getId() : null,
                branch.getRootCommit() != null ? branch.getRootCommit().getId() : null,
                branch.getLeafCommit() != null ? branch.getLeafCommit().getId() : null,
                branch.getSave() != null ? branch.getSave().getId() : null
        );
    }

    public static GraphResponse toCommitGraphResponse(String title,
            List<CommitGraphDto> commits,
            List<EdgeDto> edges,
            List<BranchGraphDto> branches
    ) {
        return new GraphResponse(title, commits, edges, branches);
    }
}
