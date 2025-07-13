package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class BranchServiceTest {

    @InjectMocks
    private BranchService branchService;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private SaveRepository saveRepository;

    @Mock
    private SaveContentRepository saveContentRepository;

    @Mock
    private CommitContentAssembler commitContentAssembler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("leaf 커밋에서 저장 생성 - 기존 브랜치에 저장 추가")
    void createBranchOrSave_leafCommit() {
        // given
        Long documentId = 1L;
        Long fromCommitId = 10L;
        BranchCreateRequest request = new BranchCreateRequest("기존 브랜치", fromCommitId);

        Doc doc = Doc.builder().build();
        ReflectionTestUtils.setField(doc, "id", documentId);

        Branch branch = Branch.builder().name("기존 브랜치").doc(doc).fromCommit(null).build();
        ReflectionTestUtils.setField(branch, "id", 101L);

        Commit commit = Commit.builder().branch(branch).commitMongoId("mongo-123").build();
        ReflectionTestUtils.setField(commit, "id", fromCommitId);
        branch.updateLeafCommit(commit);

        when(commitRepository.findById(fromCommitId)).thenReturn(Optional.of(commit));
        when(commitContentAssembler.assemble("mongo-123")).thenReturn(List.of(Map.of("type", "paragraph")));

        SaveContent savedContent = SaveContent.builder().content(Map.of()).build();
        ReflectionTestUtils.setField(savedContent, "id", "saved-mongo-id");
        when(saveContentRepository.save(any())).thenReturn(savedContent);

        Save saved = Save.builder().branch(branch).saveMongoId("saved-mongo-id").build();
        ReflectionTestUtils.setField(saved, "id", 301L);
        when(saveRepository.save(any())).thenReturn(saved);

        // when
        BranchCreateResponse response = branchService.createBranchOrSave(documentId, request);

        // then
        assertThat(response.branchId()).isEqualTo(101L);
        assertThat(response.saveId()).isEqualTo(301L);
    }
}
