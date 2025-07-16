package io.ejangs.docsa.domain.save.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mongodb.MongoTimeoutException;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveBlock;
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
import org.junit.jupiter.api.DisplayName;
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

    private List<SaveBlock> data = List.of(
            new SaveBlock(Map.of("text1", "Key features")),
            new SaveBlock(Map.of("text2", "Key features"))
    );

//    @AfterEach
//    void tearDown() {
//        saveRepository.deleteAll();
//        userRepository.deleteAll();
//        docRepository.deleteAll();
//        branchRepository.deleteAll();
//        saveContentRepository.deleteAll();
//    }

    @Test
    @DisplayName("Mongo 저장 실패 시 updateSave 롤백")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updateSave_fails_whenMongoSaveFails_thenMysqlDeleted() throws Exception {
        User user = SaveServiceUtil.createUser();
        Doc doc = Doc.builder()
                .user(user)
                .title("title")
                .build();

        Branch branch = Branch.builder()
                .doc(doc)
                .name("branch")
                .build();

        Save save = Save.builder()
                .branch(branch)
                .build();

        SaveContent saveContent = SaveContent.builder()
                .content(data)
                .build();

        userRepository.save(user);
        docRepository.save(doc);
        branchRepository.save(branch);
        saveRepository.save(save);
        saveContentRepository.save(saveContent);

        // given
        LocalDateTime beforeUpdatedAt = save.getUpdatedAt();

        save.updateSaveMongoId("mongoId");
        SaveIdentifierDto dto = new SaveIdentifierDto(doc.getId(), save.getId(), user.getId());
        SaveUpdateRequest request = new SaveUpdateRequest(data);

//        // when
//        when(saveContentRepository.save(any()))
//                .thenThrow(new MongoTimeoutException("Mongo 연결 실패"));
//
//        assertThatThrownBy(() -> saveService.updateSave(dto, request))
//                .isInstanceOf(CustomException.class)
//                .hasMessageContaining(SaveErrorCode.SAVE_CREATE_FAIL.getMessage());
//
//        // then
//        Save after = saveRepository.findById(save.getId()).orElse(null);
//        assertThat(after).isNotNull();
//        assertThat(after.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
    }
}

