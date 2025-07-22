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
import io.ejangs.docsa.domain.commit.util.CommitMockTestUtils;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockSequenceErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private Commit savedCommit;
    private CreateCommitResponse expectedResponse;
    private Edge edge;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        docId = 1L;
        branchId = 1L;

        // CreateCommitRequest 생성
        createCommitRequest = new CreateCommitRequest(
                "Test commit message",
                "",
                1L,
                List.of(CommitMockTestUtils.createBlockRequest("block1"),
                        CommitMockTestUtils.createBlockRequest("block2")),
                List.of("block1", "block2")
        );

        // User 생성
        user = CommitMockTestUtils.createUser();
        userDetails = CustomUserDetails.from(user);

        // Doc 생성
        doc = CommitMockTestUtils.createDoc(user);

        // Branch 생성
        branch = CommitMockTestUtils.createBranch(doc, baseCommit);

        // Base commit 생성
        baseCommit = CommitMockTestUtils.createBaseCommit(branch);
        baseCommit.setBranch(branch);

        // Blocks 생성
        savedBlocks = List.of(
                CommitMockTestUtils.createBlock("block1"),
                CommitMockTestUtils.createBlock("block2")
        );

        baseCommitBlocks = List.of(
                CommitMockTestUtils.createBlock("baseBlock1")
        );

        // CommitBlockSequence 생성
        savedCbs = CommitMockTestUtils.createCommitBlockSequence();

        // Commit 생성
        savedCommit = CommitMockTestUtils.createMockCommit(branch, 2L);

        // Expected response 생성
        expectedResponse = new CreateCommitResponse(1L);

        // edge 생성
        edge = CommitMockTestUtils.createEdge(doc, baseCommit, savedCommit);
    }


    @Test
    @DisplayName("커밋 생성 성공 - 기본")
    void createCommit_Success() {
        // Given
        when(docService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branch);
        when(blockService.saveBlocks(any())).thenReturn(savedBlocks);
        lenient().when(cbsRepository.findById(baseCommit.getCommitMongoId())).thenReturn(
                Optional.of(CommitMockTestUtils.createCommitBlockSequence()));

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
                    createCommitRequest, userDetails.getId());

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
        lenient().when(cbsRepository.findById(baseCommit.getCommitMongoId())).thenReturn(
                Optional.of(CommitMockTestUtils.createCommitBlockSequence()));

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
                    createCommitRequest, userDetails.getId());

            // Then
            assertThat(result).isEqualTo(expectedResponse);
            verify(saveService).deleteSaveIfExists(branch.getId());
        }
    }

    @Test
    @DisplayName("커밋 생성 성공 - leafCommit이 null인 경우 fromCommit 사용")
    void createCommit_Success_UseFromCommitWhenLeafCommitIsNull() {
        // Given
        Branch branchWithoutLeafCommit = CommitMockTestUtils.createBranch(doc, baseCommit);
        branchWithoutLeafCommit.updateLeafCommit(null); // leafCommit을 null로 설정

        when(docService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branchWithoutLeafCommit);
        when(blockService.saveBlocks(any())).thenReturn(savedBlocks);
        when(cbsRepository.findById(
                branchWithoutLeafCommit.getFromCommit().getCommitMongoId())).thenReturn(
                Optional.of(CommitMockTestUtils.createCommitBlockSequence()));

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
                    createCommitRequest, userDetails.getId());

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
        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest,
                userDetails.getId()))
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
                    savedCommit);
            when(commitRepository.save(savedCommit)).thenReturn(savedCommit);

            // When & Then
            assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest,
                    userDetails.getId()))
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
                    new RuntimeException("Block save failed"));

            // When & Then
            assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest,
                    userDetails.getId()))
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
                List.of(CommitMockTestUtils.createBlockRequest("block1")),
                List.of("block1", "invalidBlock") // 존재하지 않는 블록
        );
        try (MockedStatic<CommitBlockSequenceMapper> cbsMapperMock = mockStatic(
                CommitBlockSequenceMapper.class);
                MockedStatic<CommitMapper> commitMapperMock = mockStatic(CommitMapper.class)) {

            when(docService.getById(docId)).thenReturn(doc);
            when(branchService.getById(branchId)).thenReturn(branch);
            when(blockService.saveBlocks(any())).thenReturn(savedBlocks);
            when(CommitMapper.toEntity(branch, invalidRequest)).thenReturn(
                    savedCommit);
            when(commitRepository.save(savedCommit)).thenReturn(savedCommit);

            // When & Then
            assertThatThrownBy(() -> commitService.createCommit(docId, invalidRequest,
                    userDetails.getId()))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(
                            BlockSequenceErrorCode.BLOCK_SEQUENCE_INVALID.getMessage());

            // MongoDB에 반영 안됨
            verify(cbsRepository, never()).save(savedCbs);
        }
    }
}