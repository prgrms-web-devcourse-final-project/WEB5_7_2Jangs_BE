package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitBlockSequenceMapper;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockSequenceErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommitServiceMockTest {

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private CommitBlockSequenceRepository cbsRepository;

    @Mock
    private DocService docService;

    @Mock
    private BranchService branchService;

    @Mock
    private BlockService blockService;

    @Mock
    private SaveService saveService;

    @Mock
    private EdgeService edgeService;

    @InjectMocks
    private CommitService commitService;

    private Long docId;
    private Long branchId;
    private CreateCommitRequest createCommitRequest;
    private User user;
    private Doc doc;
    private Branch branch;
    private Commit baseCommit;
    private List<Block> savedBlocks;
    private List<Block> baseCommitBlocks;
    private CommitBlockSequence savedCbs;
    private Commit saveBeforeCommit;
    private Commit savedCommit;
    private CreateCommitResponse expectedResponse;
    private Edge edge;

    @BeforeEach
    void setUp() {
        docId = 1L;
        branchId = 1L;

        // CreateCommitRequest 생성
        createCommitRequest = new CreateCommitRequest(
                "Test commit message",
                "",
                1L,
                List.of(createBlockRequest("block1"), createBlockRequest("block2")),
                List.of("block1", "block2")
        );

        // User 생성
        user = createUser();

        // Doc 생성
        doc = createDoc();

        // Branch 생성
        branch = createBranch();

        // Base commit 생성
        baseCommit = createBaseCommit();

        // Blocks 생성
        savedBlocks = List.of(
                createBlock("block1"),
                createBlock("block2")
        );

        baseCommitBlocks = List.of(
                createBlock("baseBlock1")
        );

        // CommitBlockSequence 생성
        savedCbs = createCommitBlockSequence();

        // Commit 생성
        saveBeforeCommit = createBeforeCommit();
        savedCommit = createCommit();

        // Expected response 생성
        expectedResponse = new CreateCommitResponse(1L);

        // edge 생성
        edge = createEdge();
    }

    private Edge createEdge() {
        Edge edge = Edge.builder()
                .doc(doc)
                .prevCommit(baseCommit)
                .nextCommit(savedCommit)
                .build();
        ReflectionTestUtils.setField(edge, "id", 1L);
        return edge;
    }

    @Test
    @DisplayName("커밋 생성 성공 - 기본")
    void createCommit_Success() {
        // Given
        when(docService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branch);
        when(blockService.saveBlocks(any())).thenReturn(savedBlocks);
        when(cbsRepository.findById(baseCommit.getCommitMongoId())).thenReturn(
                Optional.of(createCommitBlockSequence()));

        try (MockedStatic<CommitBlockSequenceMapper> cbsMapperMock = mockStatic(
                CommitBlockSequenceMapper.class);
                MockedStatic<CommitMapper> commitMapperMock = mockStatic(CommitMapper.class)) {

            when(CommitBlockSequenceMapper.toEntity(anyList())).thenReturn(savedCbs);
            when(cbsRepository.save(savedCbs)).thenReturn(savedCbs);
            when(CommitMapper.toEntity(branch, createCommitRequest)).thenReturn(
                    savedCommit);
            when(commitRepository.save(savedCommit)).thenReturn(savedCommit);
            when(CommitMapper.toCreateCommitResponse(savedCommit)).thenReturn(expectedResponse);

            // When
            CreateCommitResponse result = commitService.createCommit(docId,
                    createCommitRequest);

            // Then
            assertThat(result).isEqualTo(expectedResponse);

            verify(docService).getById(docId);
            verify(branchService).getById(branchId);
            verify(blockService).saveBlocks(createCommitRequest.blocks());
            verify(cbsRepository).save(savedCbs);
            verify(commitRepository).save(savedCommit);
        }
    }

    @Test
    @DisplayName("커밋 생성 성공 - 기존 Save 삭제")
    void createCommit_Success_DeleteExistingSave() {
        // Given
        when(docService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branch);
        when(blockService.saveBlocks(any())).thenReturn(savedBlocks);
        when(cbsRepository.findById(baseCommit.getCommitMongoId())).thenReturn(
                Optional.of(createCommitBlockSequence()));

        try (MockedStatic<CommitBlockSequenceMapper> cbsMapperMock = mockStatic(
                CommitBlockSequenceMapper.class);
                MockedStatic<CommitMapper> commitMapperMock = mockStatic(CommitMapper.class)) {

            when(CommitBlockSequenceMapper.toEntity(anyList())).thenReturn(savedCbs);
            when(cbsRepository.save(savedCbs)).thenReturn(savedCbs);
            when(CommitMapper.toEntity(branch, createCommitRequest)).thenReturn(
                    savedCommit);
            when(commitRepository.save(savedCommit)).thenReturn(savedCommit);
            when(CommitMapper.toCreateCommitResponse(savedCommit)).thenReturn(expectedResponse);

            // When
            CreateCommitResponse result = commitService.createCommit(docId,
                    createCommitRequest);

            // Then
            assertThat(result).isEqualTo(expectedResponse);
            verify(saveService).deleteSaveIfExists(branch.getId());
        }
    }

    @Test
    @DisplayName("커밋 생성 성공 - leafCommit이 null인 경우 fromCommit 사용")
    void createCommit_Success_UseFromCommitWhenLeafCommitIsNull() {
        // Given
        Branch branchWithoutLeafCommit = createBranch();
        branchWithoutLeafCommit.updateLeafCommit(null); // leafCommit을 null로 설정

        when(docService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branchWithoutLeafCommit);
        when(blockService.saveBlocks(any())).thenReturn(savedBlocks);
        when(cbsRepository.findById(
                branchWithoutLeafCommit.getFromCommit().getCommitMongoId())).thenReturn(
                Optional.of(createCommitBlockSequence()));

        try (MockedStatic<CommitBlockSequenceMapper> cbsMapperMock = mockStatic(
                CommitBlockSequenceMapper.class);
                MockedStatic<CommitMapper> commitMapperMock = mockStatic(CommitMapper.class)) {

            when(CommitBlockSequenceMapper.toEntity(anyList())).thenReturn(savedCbs);
            when(cbsRepository.save(savedCbs)).thenReturn(savedCbs);
            when(CommitMapper.toEntity(branchWithoutLeafCommit, createCommitRequest)).thenReturn(
                    savedCommit);
            when(commitRepository.save(savedCommit)).thenReturn(savedCommit);
            when(CommitMapper.toCreateCommitResponse(savedCommit)).thenReturn(expectedResponse);

            // When
            CreateCommitResponse result = commitService.createCommit(docId,
                    createCommitRequest);

            // Then
            assertThat(result).isEqualTo(expectedResponse);
            verify(cbsRepository).findById(
                    branchWithoutLeafCommit.getFromCommit().getCommitMongoId());
        }
    }

    @Test
    @DisplayName("커밋 생성 실패 - 문서 검증 실패")
    void createCommit_Fail_DocNotFound() {
        // Given
        doThrow(new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_NOT_FOUND))
                .when(docService).getById(docId);

        // When & Then
        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest))
                .isInstanceOf(CustomException.class);

        verify(docService).getById(docId);
        verify(branchService, never()).getById(any());
        verify(blockService, never()).saveBlocks(any());
    }

    @Test
    @DisplayName("커밋 생성 실패 - 블록 저장 실패시")
    void createCommit_Fail_BlockSaveFails_ShouldRollback() {
        // Given
        when(docService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branch);
        when(blockService.saveBlocks(any())).thenThrow(new RuntimeException("Block save failed"));

        try (MockedStatic<CommitBlockSequenceMapper> cbsMapperMock = mockStatic(
                CommitBlockSequenceMapper.class);
                MockedStatic<CommitMapper> commitMapperMock = mockStatic(CommitMapper.class)) {

            when(CommitMapper.toEntity(branch, createCommitRequest)).thenReturn(
                    saveBeforeCommit);
            when(commitRepository.save(saveBeforeCommit)).thenReturn(savedCommit);

            // When & Then
            assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage(CommitErrorCode.FAIL_CREATE_COMMIT.getMessage());

            verify(docService).getById(docId);
            verify(branchService).getById(branchId);
            verify(blockService).saveBlocks(createCommitRequest.blocks());
            verify(cbsRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("커밋 생성 실패 - JPA 저장 실패시 MongoDB 호출 안함")
    void createCommit_Fail_JpaSaveFails_ShouldRollbackMongoDB() {
        // Given
        when(docService.getById(docId)).thenReturn(doc);
        lenient().when(branchService.getById(branchId)).thenReturn(branch);
        lenient().when(blockService.saveBlocks(any())).thenReturn(savedBlocks);

        try (MockedStatic<CommitBlockSequenceMapper> cbsMapperMock = mockStatic(
                CommitBlockSequenceMapper.class);
                MockedStatic<CommitMapper> commitMapperMock = mockStatic(CommitMapper.class)) {

            when(CommitBlockSequenceMapper.toEntity(anyList())).thenReturn(savedCbs);
            when(CommitMapper.toEntity(branch, createCommitRequest)).thenReturn(
                    savedCommit);
            when(commitRepository.save(savedCommit)).thenThrow(
                    new RuntimeException("JPA save failed"));

            // When & Then
            assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage(CommitErrorCode.FAIL_CREATE_COMMIT.getMessage());

            // MongoDB에 반영 안됨
            verify(cbsRepository, never()).save(savedCbs);
        }
    }

    @Test
    @DisplayName("커밋 생성 실패 - 잘못된 블록 순서")
    void createCommit_Fail_InvalidBlockOrder() {
        // Given
        CreateCommitRequest invalidRequest = new CreateCommitRequest(
                "Test commit message",
                "",
                1L,
                List.of(createBlockRequest("block1")),
                List.of("block1", "invalidBlock") // 존재하지 않는 블록
        );

        when(docService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branch);
        when(blockService.saveBlocks(any())).thenReturn(savedBlocks);
        when(cbsRepository.findById(baseCommit.getCommitMongoId())).thenReturn(
                Optional.of(createCommitBlockSequence()));

        try (MockedStatic<CommitBlockSequenceMapper> cbsMapperMock = mockStatic(
                CommitBlockSequenceMapper.class);
                MockedStatic<CommitMapper> commitMapperMock = mockStatic(CommitMapper.class)) {

            when(CommitMapper.toEntity(branch, invalidRequest)).thenReturn(
                    saveBeforeCommit);
            when(commitRepository.save(saveBeforeCommit)).thenReturn(savedCommit);

            // When & Then
            assertThatThrownBy(() -> commitService.createCommit(docId, invalidRequest))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(
                            BlockSequenceErrorCode.BLOCK_SEQUENCE_INVALID.getMessage());

            // MongoDB에 반영 안됨
            verify(cbsRepository, never()).save(savedCbs);
        }


    }

    private User createUser() {
        User user = User.builder()
                .email("user@ejangs.io")
                .password("userPassword123")
                .name("user")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private BlockDto createBlockRequest(String blockId) {
        Map<String, Object> blockData = new HashMap<>();
        blockData.put("id", blockId);
        blockData.put("type", "text");
        blockData.put("content", "Test content");
        return new BlockDto(blockData);
    }

    private Block createBlock(String blockId) {
        Map<String, Object> content = new HashMap<>();
        content.put("id", blockId);
        content.put("type", "text");
        content.put("content", "Test content");
        return Block.builder()
                .content(content)
                .build();
    }

    private Doc createDoc() {
        Doc doc = Doc.builder()
                .title("doc-title")
                .user(user)
                .build();
        ReflectionTestUtils.setField(doc, "id", 1L);
        return doc;
    }

    private Branch createBranch() {
        Branch branch = Branch.builder()
                .fromCommit(baseCommit)
                .doc(doc)
                .build();
        ReflectionTestUtils.setField(branch, "id", 1L);
        ReflectionTestUtils.setField(branch, "leafCommit", baseCommit);
        return branch;
    }

    private Commit createBaseCommit() {
        Commit commit = Commit.builder()
                .commitMongoId("mongo-commit-id")
                .branch(branch)
                .build();
        ReflectionTestUtils.setField(commit, "id", 1L);
        return commit;
    }

    private CommitBlockSequence createCommitBlockSequence() {
        CommitBlockSequence cbs = CommitBlockSequence.builder()
                .blockOrders(List.of("block1", "block2"))
                .build();
        ReflectionTestUtils.setField(cbs, "id", "cbs-id");
        return cbs;
    }

    private Commit createBeforeCommit() {
        return Commit.builder()
                .title("Test commit message")
                .commitMongoId("mongo-commit-id")
                .branch(branch)
                .build();
    }

    private Commit createCommit() {
        Commit commit = Commit.builder()
                .title("Test commit message")
                .commitMongoId("mongo-commit-id")
                .branch(branch)
                .build();
        ReflectionTestUtils.setField(commit, "id", 1L);
        return commit;
    }
}