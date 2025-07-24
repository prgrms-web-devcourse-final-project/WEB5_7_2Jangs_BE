package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EdgeService {

    private final EdgeRepository edgeRepository;

    public Edge saveEdge(Edge edge) {
        return edgeRepository.save(edge);
    }

    public List<Commit> cutEdge(Doc doc, Long commitId) {

        List<Edge> edges = edgeRepository.findByNextCommitId(commitId);
        List<Edge> prevEdges = edgeRepository.findByPrevCommitId(commitId);

        List<Commit> commits = edges.stream()
                .map(Edge::getPrevCommit)
                .toList();

        edges.addAll(prevEdges);

        for (Edge edge : edges) {
            doc.removeEdge(edge);
        }

        edgeRepository.deleteAll(edges);

        return commits;
    }
}
