package io.ejangs.docsa.domain.commit.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.block.entity.Block;
import io.ejangs.docsa.domain.commit.dao.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.entity.CommitBlockSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitContentAssemblerTest {

    @Mock
    private CommitBlockSequenceRepository cbsRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CommitContentAssembler assembler;

    private Commit fakeCommit;

    @BeforeEach
    void setUp() {
        fakeCommit = Commit.builder().title("테스트 커밋").description("desc").branch(null).build();
    }

    @Test
    void assemble_withTwoBlocks_producesExpectedJsonArray() throws Exception {

        Block b1 = Block.builder().uniqueId("mhTl6ghSkV").type("paragraph")
                .data("{\"text\":\"First block\"}").document(null).build();
        Block b2 = Block.builder().uniqueId("os_YI4eub4").type("list")
                .data("{\"type\":\"unordered\",\"items\":[\"A\",\"B\"]}").document(null).build();

        ReflectionTestUtils.setField(b1, "id", 1L);
        ReflectionTestUtils.setField(b2, "id", 2L);

        //CommitBlockSequence 두 건(순서, next 연결) 만들기
        CommitBlockSequence seq1 =
                CommitBlockSequence.builder().currentBlock(b1).first(true).nextBlock(b2).build();
        CommitBlockSequence seq2 =
                CommitBlockSequence.builder().currentBlock(b2).first(false).nextBlock(null).build();

        List<CommitBlockSequence> seqs = Arrays.asList(seq1, seq2);

        when(cbsRepository.findByCommit(fakeCommit)).thenReturn(seqs);

        assembler = new CommitContentAssembler(cbsRepository, new ObjectMapper());

        // assemble 호출
        String json = assembler.assemble(fakeCommit);

        // Jackson으로 파싱해서 검증
        JsonNode root = new ObjectMapper().readTree(json);
        assertTrue(root.isArray());
        assertEquals(2, root.size());

        JsonNode first = root.get(0);
        assertEquals("mhTl6ghSkV", first.get("id").asText());
        assertEquals("paragraph", first.get("type").asText());
        assertEquals("First block", first.get("data").get("text").asText());

        JsonNode second = root.get(1);
        assertEquals("os_YI4eub4", second.get("id").asText());
        assertEquals("list", second.get("type").asText());
        assertTrue(second.get("data").get("items").isArray());
        assertEquals("A", second.get("data").get("items").get(0).asText());
        assertEquals("B", second.get("data").get("items").get(1).asText());
    }
}
