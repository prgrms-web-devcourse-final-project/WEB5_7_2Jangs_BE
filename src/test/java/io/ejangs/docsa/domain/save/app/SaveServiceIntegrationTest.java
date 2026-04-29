package io.ejangs.docsa.domain.save.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mongodb.DuplicateKeyException;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveIdentifierDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveServiceUtil;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SaveServiceIntegrationTest {

    @Autowired
    private SaveService saveService;

    @Autowired
    private SaveRepository saveRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocRepository docRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CommitRepository commitRepository;

    @Autowired
    private EntityManager em;

    @MockitoBean
    private SaveContentRepository saveContentRepository;

    private final List<Map<String, Object>> data = List.of(
            Map.of("text1", "Key features"),
            Map.of("text2", "Key features")
    );

    private User user;
    private Doc doc;
    private Branch branch;
    private Save save;
    private SaveContent saveContent;
    private Commit commit;

    @BeforeEach
    void init() {
        user = SaveServiceUtil.createUser();
        doc = SaveServiceUtil.createDoc(user);
        branch = SaveServiceUtil.createDefaultBranch(doc);
        save = SaveServiceUtil.createSave(branch);
        saveContent = SaveServiceUtil.createSaveContent();
        commit = SaveServiceUtil.createCommit(branch);
        save.updateSaveMongoId("mongoId");

        userRepository.save(user);
        docRepository.save(doc);
        branchRepository.save(branch);
        saveRepository.save(save);
        saveContentRepository.save(saveContent);
        commitRepository.save(commit);
    }

    @Nested
    @DisplayName("save update 실패 테스트")
    class SaveRollbackTest {

        @Autowired
        private SaveService saveService;

        @Autowired
        private SaveRepository saveRepository;

        @Test
        @DisplayName("Mongo 저장 실패 시 updateSave 롤백")
        //@Transactional(propagation = Propagation.NOT_SUPPORTED)
        void updateSave_fails_whenMongoSaveFails_thenMysqlDeleted() throws Exception {
            // given
            LocalDateTime beforeUpdatedAt = save.getUpdatedAt();

            SaveIdentifierDto dto = new SaveIdentifierDto(doc.getId(), save.getId(), user.getId());
            SaveUpdateRequest request = new SaveUpdateRequest(data);

            // when
            when(saveContentRepository.findById(save.getSaveMongoId())).thenReturn(
                    Optional.of(saveContent));

            when(saveContentRepository.save(any()))
                    .thenThrow(new RecoverableDataAccessException("mongo write failed"));

            assertThatThrownBy(() -> saveService.updateSave(dto, request))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(SaveErrorCode.FAIL_TO_SAVE.getMessage());

            em.clear();

            Save after = saveRepository.findById(save.getId()).orElse(null);
            assertThat(after).isNotNull();
            assertThat(SaveServiceUtil.trimToMillis(after.getUpdatedAt())).isEqualTo(SaveServiceUtil.trimToMillis(beforeUpdatedAt));
        }
    }

    @Test
    @DisplayName("저장 생성 실패 - saveMongoId는 null일 수 없다")
    void createSave_shouldFail_whenSaveMongoIdIsNull() {
        Branch newBranch = Branch.builder()
                .doc(doc)
                .name("null-save-mongo-id")
                .build();
        branchRepository.saveAndFlush(newBranch);

        Save invalidSave = Save.builder()
                .branch(newBranch)
                .build();

        assertThatThrownBy(() -> saveRepository.saveAndFlush(invalidSave))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
