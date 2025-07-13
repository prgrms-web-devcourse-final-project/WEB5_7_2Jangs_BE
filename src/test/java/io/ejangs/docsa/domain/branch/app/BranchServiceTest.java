package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.dao.mysql.DocumentRepository;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private CommitRepository commitRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private SaveRepository saveRepository;
    @Mock private CommitContentAssembler assembler;

    @InjectMocks
    private BranchService service;

    private Doc doc;
    private Branch existingBranch;
    private Commit fromCommit;
    private Branch newBranch;

    @BeforeEach
    void setUp() {
        doc = Doc.builder().title("D").build();
        ReflectionTestUtils.setField(doc, "id", 1L);
        lenient().when(documentRepository.findById(1L))
                .thenReturn(Optional.of(doc));

        existingBranch = Branch.builder()
                .name("b1")
                .document(doc)
                .fromCommit(null)
                .build();
        ReflectionTestUtils.setField(existingBranch, "id", 100L);

        fromCommit = Commit.builder()
                .title("c")
                .description("d")
                .branch(existingBranch)
                .build();
        ReflectionTestUtils.setField(fromCommit, "id", 10L);

        ReflectionTestUtils.setField(existingBranch, "leafCommit", fromCommit);

        newBranch = Branch.builder()
                .name("b2")
                .document(doc)
                .fromCommit(fromCommit)
                .build();
        ReflectionTestUtils.setField(newBranch, "id", 200L);
    }

    @Test
    @DisplayName("fromCommitId null → 새 브랜치 + 빈 content 반환")
    void whenFromIdNull_createNewBranchWithEmptyContent() {
        when(branchRepository.save(any(Branch.class)))
                .thenReturn(newBranch);

        Save blankSave = Save.builder()
                .content("")
                .branch(newBranch)
                .build();
        ReflectionTestUtils.setField(blankSave, "id", 300L);
        when(saveRepository.save(any(Save.class)))
                .thenReturn(blankSave);

        BranchCreateRequest req = new BranchCreateRequest("b-new", null);
        BranchCreateResponse resp = service.createBranch(1L, req);

        assertThat(resp.branchId()).isEqualTo(200L);
        assertThat(resp.saveId()).isEqualTo(300L);

        verify(branchRepository).save(argThat(b -> b.getName().equals("b-new")));
        verify(saveRepository).save(argThat(s -> s.getContent().equals("")));
        verifyNoMoreInteractions(commitRepository, assembler);
    }

    @Test
    @DisplayName("fromCommitId leaf → 기존 브랜치 + assembled content 반환")
    void whenFromIdIsLeaf_useExistingBranch() {
        when(commitRepository.findById(10L))
                .thenReturn(Optional.of(fromCommit));

        when(assembler.assemble(fromCommit))
                .thenReturn("assembled");

        Save leafSave = Save.builder()
                .content("assembled")
                .branch(existingBranch)
                .build();
        ReflectionTestUtils.setField(leafSave, "id", 301L);
        when(saveRepository.save(any(Save.class)))
                .thenReturn(leafSave);

        BranchCreateRequest req = new BranchCreateRequest("ignored", 10L);
        BranchCreateResponse resp = service.createBranch(1L, req);

        assertThat(resp.branchId()).isEqualTo(100L);
        assertThat(resp.saveId()).isEqualTo(301L);

        verify(commitRepository).findById(10L);
        verify(assembler).assemble(fromCommit);
        verify(saveRepository).save(argThat(s -> s.getBranch().equals(existingBranch)));
        verifyNoMoreInteractions(branchRepository);
    }

    @Test
    @DisplayName("fromCommitId non-leaf → 새 브랜치 + assembled content 반환")
    void whenFromIdNotLeaf_createBranchFromCommit() {

        ReflectionTestUtils.setField(existingBranch, "leafCommit", null);

        when(commitRepository.findById(10L))
                .thenReturn(Optional.of(fromCommit));

        when(assembler.assemble(fromCommit))
                .thenReturn("assembled2");

        when(branchRepository.save(any(Branch.class)))
                .thenReturn(newBranch);

        Save newSave = Save.builder()
                .content("assembled2")
                .branch(newBranch)
                .build();
        ReflectionTestUtils.setField(newSave, "id", 302L);
        when(saveRepository.save(any(Save.class)))
                .thenReturn(newSave);

        BranchCreateRequest req = new BranchCreateRequest("b3", 10L);
        BranchCreateResponse resp = service.createBranch(1L, req);

        assertThat(resp.branchId()).isEqualTo(200L);
        assertThat(resp.saveId()).isEqualTo(302L);

        verify(commitRepository).findById(10L);
        verify(branchRepository).save(any(Branch.class));
        verify(saveRepository).save(argThat(s -> s.getBranch().equals(newBranch)));
    }

    @Test
    @DisplayName("문서가 없으면 NOT_FOUND 예외 발생")
    void whenDocumentNotFound_throw() {
        when(documentRepository.findById(2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createBranch(2L, new BranchCreateRequest("x", null))
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(DocumentErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("커밋이 없으면 NOT_FOUND 예외 발생")
    void whenCommitNotFound_throw() {
        when(commitRepository.findById(20L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createBranch(1L, new BranchCreateRequest("x", 20L))
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommitErrorCode.COMMIT_NOT_FOUND);
    }
}
