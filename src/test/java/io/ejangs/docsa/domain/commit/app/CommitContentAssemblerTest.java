package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class CommitContentAssemblerTest {

    @InjectMocks
    private CommitContentAssembler assembler;

    @Mock
    private CommitBlockSequenceRepository commitBlockSequenceRepository;

    @Mock
    private BlockRepository blockRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("정상적으로 블록을 조립하여 리스트 반환")
    void assemble_success() {
        // given
        String commitMongoId = "commit123";
        List<String> blockIds = List.of("block1", "block2");

        CommitBlockSequence seq = CommitBlockSequence.builder().blockOrders(blockIds).build();
        when(commitBlockSequenceRepository.findById(commitMongoId)).thenReturn(Optional.of(seq));

        Block block1 = Block.builder().content(Map.of("text", "hello")).build();
        Block block2 = Block.builder().content(Map.of("text", "world")).build();
        ReflectionTestUtils.setField(block1, "id", "block1");
        ReflectionTestUtils.setField(block2, "id", "block2");

        when(blockRepository.findAllById(blockIds)).thenReturn(List.of(block1, block2));

        // when
        List<Map<String, Object>> result = assembler.assemble(commitMongoId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("text")).isEqualTo("hello");
        assertThat(result.get(1).get("text")).isEqualTo("world");
    }

    @Test
    @DisplayName("커밋 MongoID가 존재하지 않으면 예외 발생")
    void assemble_commitNotFound() {
        // given
        String commitMongoId = "nonexistent";
        when(commitBlockSequenceRepository.findById(commitMongoId)).thenReturn(Optional.empty());

        // expect
        assertThrows(CustomException.class, () -> assembler.assemble(commitMongoId));
    }

    @Test
    @DisplayName("블록 일부가 없으면 예외 발생")
    void assemble_blockMissing() {
        // given
        String commitMongoId = "commit456";
        List<String> blockIds = List.of("block1", "block2");

        CommitBlockSequence seq = CommitBlockSequence.builder().blockOrders(blockIds).build();
        when(commitBlockSequenceRepository.findById(commitMongoId)).thenReturn(Optional.of(seq));

        Block block1 = Block.builder().content(Map.of("text", "present")).build();
        ReflectionTestUtils.setField(block1, "id", "block1");

        // block2 누락
        when(blockRepository.findAllById(blockIds)).thenReturn(List.of(block1));

        // expect
        assertThrows(CustomException.class, () -> assembler.assemble(commitMongoId));
    }
}
