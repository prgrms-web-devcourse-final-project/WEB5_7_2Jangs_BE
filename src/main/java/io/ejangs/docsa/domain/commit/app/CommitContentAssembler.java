package io.ejangs.docsa.domain.commit.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommitContentAssembler {
    private final CommitBlockSequenceRepository cbsRepository;
    private final ObjectMapper objectMapper;

    public String assemble(Commit commit) {
        List<CommitBlockSequence> seqs = cbsRepository.findByCommit(commit);
        if (seqs.isEmpty()) return "[]";

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
        if (cur == null) throw new CustomException(BlockErrorCode.BLOCK_NOT_FOUND);

        ArrayNode array = objectMapper.createArrayNode();

        while (true) {
            Block blk = cur.getCurrentBlock();

            // ObjectNode 생성
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", blk.getUniqueId());
            node.put("type", blk.getType());

            // data 필드 처리
            String rawData = blk.getData();
            if (rawData != null && rawData.trim().startsWith("{")) {
                try {
                    JsonNode dataNode = objectMapper.readTree(rawData);
                    node.set("data", dataNode);
                } catch (JsonProcessingException e) {
                    throw new CustomException(DocumentErrorCode.JSON_SERIALIZATION_FAILED);
                }
            } else {
                node.put("data", objectMapper.createObjectNode());
            }
            // tunes 필드 처리
            String rawTunes = blk.getTunes();
            if (rawTunes != null && rawTunes.trim().startsWith("{")) {
                try {
                    JsonNode tunesNode = objectMapper.readTree(rawTunes);
                    node.set("tunes", tunesNode);
                } catch (JsonProcessingException e) {
                    throw new CustomException(DocumentErrorCode.JSON_SERIALIZATION_FAILED);
                }
            } else {
                // tunes가 없으면 빈 객체로
                node.set("tunes", objectMapper.createObjectNode());
            }

            array.add(node);

            Block next = cur.getNextBlock();
            if (next == null) break;
            cur = cbsMap.get(next.getId());
        }

        // 최종 JSON 문자열 반환
        try {
            return objectMapper.writeValueAsString(array);
        } catch (JsonProcessingException e) {
            throw new CustomException(DocumentErrorCode.JSON_SERIALIZATION_FAILED);
        }
    }
}
