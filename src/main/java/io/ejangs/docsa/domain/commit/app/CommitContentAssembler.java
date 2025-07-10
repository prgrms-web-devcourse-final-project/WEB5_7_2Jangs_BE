package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.block.entity.Block;
import io.ejangs.docsa.domain.commit.dao.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.entity.CommitBlockSequence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommitContentAssembler {
    private final CommitBlockSequenceRepository cbsRepository;

    public String assemble(Commit commit) {
        List<CommitBlockSequence> seqs = cbsRepository.findByCommit(commit);
        if (seqs.isEmpty()) return "";

        Map<Long, CommitBlockSequence> cbsMap =
                seqs.stream().collect(Collectors.toMap(
                        s -> s.getCurrentBlock().getId(), s -> s
                ));

        CommitBlockSequence cur = null;
        for (CommitBlockSequence seq : seqs) {
            if (seq.getFirst()) {
                cur = seq;
                break;
            }
        }
        if (cur == null) {
            throw new IllegalStateException("첫 블록이 없습니다");
        }

        StringBuilder sb = new StringBuilder();
        while (cur != null) {
            sb.append(cur.getCurrentBlock().getData());
            Block next = cur.getNextBlock();
            if (next == null) break;
            cur = cbsMap.get(next.getId());
        }
        return sb.toString();
    }


}

