package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Edge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EdgeService {

    private final EdgeRepository edgeRepository;

    public Edge saveEdge(Edge edge) {
        return edgeRepository.save(edge);
    }
}
