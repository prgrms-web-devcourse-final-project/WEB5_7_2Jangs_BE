package io.ejangs.docsa.domain.commit.app;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommitServiceMockTest {

//    @InjectMocks
//    private CommitService commitService;
//
//    @Mock
//    private CommitRepository commitRepository;
//    @Mock
//    private DocumentRepository documentRepository;
//    @Mock
//    private BranchRepository branchRepository;
//    @Mock
//    private BlockRepository blockRepository;
//    @Mock
//    private SaveRepository saveRepository;
//
//    private Doc doc;
//    private Branch branch;
//    private Commit commit;
//
//    @BeforeEach
//    void setup() {
//        doc = mock(Doc.class);
//        branch = mock(Branch.class);
//        commit = mock(Commit.class);
//    }
//
//    @Test
//    void createCommit_success() {
//        // given
//        Long documentId = 1L;
//        Long userId = 10L;
//
//        List<BlockDto> blocks = List.of(
//                new BlockDto("abc123", "paragraph", Map.of("text", "hello"), null),
//                new BlockDto("def456", "list", Map.of("items", List.of("item1", "item2")), null)
//        );
//        List<String> blockOrders = List.of("abc123", "def456");
//
//        CreateCommitRequest request = getCreateCommitRequest(blocks, blockOrders);
//
//        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
//        when(branchRepository.findById(documentId)).thenReturn(Optional.of(branch));
//        when(saveRepository.findByBranchId(any())).thenReturn(Optional.empty());
//
//        Block block1 = mock(Block.class);
//        Block block2 = mock(Block.class);
//
//        when(blockRepository.save(any())).thenReturn(block1).thenReturn(block2);
//        when(blockRepository.findLatestByUniqueId("abc123")).thenReturn(Optional.of(block1));
//        when(blockRepository.findLatestByUniqueId("def456")).thenReturn(Optional.of(block2));
//        when(commitRepository.save(commit)).thenReturn(commit);
//
//        try (
//                MockedStatic<CommitMapper> mockedCommitMapper = mockStatic(CommitMapper.class);
//                MockedStatic<BlockMapper> mockedBlockMapper = mockStatic(BlockMapper.class)
//        ) {
//            mockedCommitMapper.when(() -> CommitMapper.toEntity(branch, request))
//                    .thenReturn(commit);
//            mockedCommitMapper.when(() -> CommitMapper.toCreateCommitResponse(commit))
//                    .thenReturn(new CreateCommitResponse(100L));
//
//            mockedBlockMapper.when(() -> BlockMapper.toEntity(doc, blocks.get(0)))
//                    .thenReturn(block1);
//            mockedBlockMapper.when(() -> BlockMapper.toEntity(doc, blocks.get(1)))
//                    .thenReturn(block2);
//
//            // when
//            CreateCommitResponse result = commitService.createCommit(documentId, userId, request);
//
//            // then
//            assertThat(result.id()).isEqualTo(100L);
//            verify(commitRepository, times(1)).save(commit);
//            verify(blockRepository, times(2)).save(any(Block.class));
//            verify(blockRepository, times(2)).findLatestByUniqueId(anyString());
//        }
//    }
//
//    @Test
//    void createCommit_fail_Document_NotFound() {
//        // given
//        Long documentId = 1L;
//        Long userId = 10L;
//        CreateCommitRequest request = getCreateCommitRequest();
//
//        when(documentRepository.findById(documentId))
//                .thenReturn(Optional.empty());
//
//        // when & then
//        CustomException exception = Assertions.assertThrows(CustomException.class,
//                () -> commitService.createCommit(documentId, userId, request));
//
//        assertThat(exception.getErrorCode()).isEqualTo(DocumentErrorCode.DOCUMENT_NOT_FOUND);
//    }
//
//    @Test
//    void createCommit_fail_Branch_NotFound() {
//        // given
//        Long documentId = 1L;
//        Long userId = 10L;
//        CreateCommitRequest request = getCreateCommitRequest();
//
//        when(documentRepository.findById(documentId))
//                .thenReturn(Optional.of(mock(Doc.class)));
//
//        when(branchRepository.findById(documentId))
//                .thenReturn(Optional.empty());
//
//        // when & then
//        CustomException exception = Assertions.assertThrows(CustomException.class,
//                () -> commitService.createCommit(documentId, userId, request));
//
//        assertThat(exception.getErrorCode()).isEqualTo(BranchErrorCode.BRANCH_NOT_FOUND);
//    }
//
//    @Test
//    void createCommit_fail_Block_Not_Found() {
//        // given
//        Long documentId = 1L;
//        Long userId = 10L;
//
//        List<BlockDto> blocks = List.of(
//                new BlockDto("abc123", "paragraph", Map.of("text", "test"), null)
//        );
//        List<String> blockOrders = List.of("abc123");
//
//        CreateCommitRequest request = getCreateCommitRequest(blocks, blockOrders);
//
//        Doc doc = mock(Doc.class);
//        Branch branch = mock(Branch.class);
//        Commit commit = mock(Commit.class);
//        Block block = mock(Block.class);
//
//        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
//        when(branchRepository.findById(documentId)).thenReturn(Optional.of(branch));
//        lenient().when(saveRepository.findByBranchId(any())).thenReturn(Optional.empty());
//
//        try (
//                MockedStatic<CommitMapper> commitMapper = mockStatic(CommitMapper.class);
//                MockedStatic<BlockMapper> blockMapper = mockStatic(BlockMapper.class)
//        ) {
//            commitMapper.when(() -> CommitMapper.toEntity(branch, request))
//                    .thenReturn(commit);
//
//            blockMapper.when(() -> BlockMapper.toEntity(doc, blocks.get(0)))
//                    .thenReturn(block);
//
//            when(blockRepository.save(any())).thenReturn(block);
//            when(blockRepository.findLatestByUniqueId("abc123"))
//                    .thenReturn(Optional.empty()); // ✅ Block 못 찾는 상황
//
//            // when & then
//            CustomException exception = Assertions.assertThrows(CustomException.class,
//                    () -> commitService.createCommit(documentId, userId, request));
//
//            assertThat(exception.getErrorCode()).isEqualTo(BlockErrorCode.BLOCK_NOT_FOUND);
//        }
//    }
//
//    private static CreateCommitRequest getCreateCommitRequest() {
//        return new CreateCommitRequest(
//                "제목", "설명", 1L,
//                List.of(), List.of()
//        );
//    }
//
//    private static CreateCommitRequest getCreateCommitRequest(List<BlockDto> blocks,
//            List<String> blockOrders) {
//        return new CreateCommitRequest(
//                "제목", "설명", 1L, blocks, blockOrders
//        );
//    }
}
