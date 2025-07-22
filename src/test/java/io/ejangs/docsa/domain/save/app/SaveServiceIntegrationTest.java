package io.ejangs.docsa.domain.save.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.mongodb.MongoTimeoutException;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
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

    @BeforeEach
    void init() {
        user = SaveServiceUtil.createUser();
        doc = SaveServiceUtil.createDoc(user);
        branch = SaveServiceUtil.createBranch(doc);
        save = SaveServiceUtil.createSave(branch);
        saveContent = SaveServiceUtil.createSaveContent();
        save.updateSaveMongoId("mongoId");

        userRepository.save(user);
        docRepository.save(doc);
        branchRepository.save(branch);
        saveRepository.save(save);
        saveContentRepository.save(saveContent);
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
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void updateSave_fails_whenMongoSaveFails_thenMysqlDeleted() throws Exception {
            // given
            LocalDateTime beforeUpdatedAt = save.getUpdatedAt();

            SaveIdentifierDto dto = new SaveIdentifierDto(doc.getId(), save.getId(), user.getId());
            SaveUpdateRequest request = new SaveUpdateRequest(data);

            // when
            when(saveContentRepository.findById(save.getSaveMongoId())).thenReturn(
                    Optional.of(saveContent));

            when(saveContentRepository.save(any()))
                    .thenThrow(new CustomException(SaveErrorCode.FAILED_TO_SAVE_IN_MONGO));

            assertThatThrownBy(() -> saveService.updateSave(dto, request))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(SaveErrorCode.FAILED_TO_SAVE_IN_MONGO.getMessage());

//            // then
//            Save after = saveRepository.findById(save.getId()).orElse(null);
//            assertThat(after).isNotNull();
//            assertThat(after.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        }
    }

    @Test
    @DisplayName("deleteSave 성공")
    void deleteSave_success() throws Exception {
        SaveIdentifierDto dto = new SaveIdentifierDto(doc.getId(), save.getId(), user.getId());
        saveService.deleteSave(dto);

        Optional<Save> optionalSave = saveRepository.findById(save.getId());
        assertThat(optionalSave.isEmpty()).isTrue();

        Optional<SaveContent> optionalSaveContent = saveContentRepository.findById(
                saveContent.getId());
        assertThat(optionalSaveContent.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("저장 삭제 실패 - 존재하지 않는 저장")
    void deleteSave_shouldFail_whenSaveNotFound() {
        // given
        SaveIdentifierDto dto = new SaveIdentifierDto(
                999L, // 존재하지 않는 Save ID
                1L,
                1L
        );

        // when & then
        assertThatThrownBy(() -> saveService.deleteSave(dto))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(SaveErrorCode.SAVE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("저장 삭제 실패 - 해당 유저의 저장이 아니다")
    void deleteSave_shouldFail_whenUserIsNotOwner() {
        // given
        SaveIdentifierDto dto = new SaveIdentifierDto(
                999L,   // 다른 사용자의 save
                save.getId(),
                user.getId()
        );

        // when & then
        assertThatThrownBy(() -> saveService.deleteSave(dto))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(SaveErrorCode.SAVE_NOT_OWNER.getMessage());
    }
}

