package io.ejangs.docsa.domain.save.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dao.mysql.DocumentRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.SaveRepositoryAdapterImpl;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveBlock;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SaveServiceUnitTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private SaveRepositoryAdapterImpl saveRepository;
    @Mock
    private Doc mockDoc;
    @Mock
    private User mockUser;
    @Mock
    private Save mockSave;
    @Mock
    private Branch mockBranch;
    @Mock
    private SaveContent mockSaveContent;
    @InjectMocks
    private SaveService saveService;

    private SaveUpdateIdDto dto;
    private List<SaveBlock> data;
    private SaveUpdateRequest request;

    private Long userId = 1L;
    private Long docId = 2L;
    private Long saveId = 3L;

    @BeforeEach
    void setUp() {
        dto = SaveUpdateIdDto.of(docId, saveId, userId);
        data = List.of(
                new SaveBlock("id1", "type1", Map.of("text1", "Key features")),
                new SaveBlock("id2", "type2", Map.of("text2", "Key features"))
        );
        request = new SaveUpdateRequest(data);
    }

    @Test
    @DisplayName("성공적인 updateSave")
    void updateSave_success() {
        when(userRepository.findById(dto.userId())).thenReturn(Optional.of(mockUser));
        when(documentRepository.findById(dto.documentId())).thenReturn(Optional.of(mockDoc));
        when(saveRepository.findSaveById(dto.saveId())).thenReturn(mockSave);
        when(saveRepository.findSaveContentById(mockSave.getSaveMongoId())).thenReturn(
                mockSaveContent);

        when(mockSave.getBranch()).thenReturn(mockBranch);
        when(mockBranch.getDoc()).thenReturn(mockDoc);
        when(mockDoc.getUser()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(userId);

        saveService.updateSave(dto, request);

        verify(saveRepository).updateSave(mockSave, mockSaveContent, data);
    }

    @Test
    @DisplayName("존재하지 않는 유저 ID로 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidUser() {
        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 문서 ID로 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidDocument() {
        when(userRepository.findById(dto.userId())).thenReturn(Optional.of(mockUser));

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 문서를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 저장 ID로 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidSave() {
        when(userRepository.findById(dto.userId())).thenReturn(Optional.of(mockUser));
        when(documentRepository.findById(dto.documentId())).thenReturn(Optional.of(mockDoc));
        when(saveRepository.findSaveById(dto.saveId()))
                .thenThrow(new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 저장 데이터를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("소유자가 아닌 유저가 save를 수정하려 할 경우 예외 발생")
    void updateSave_throwsException_ifNotOwner() {
        // given
        SaveUpdateIdDto dto = SaveUpdateIdDto.of(userId, docId, saveId);
        SaveUpdateRequest request = new SaveUpdateRequest(List.of());

        User owner = User.builder().build(); // 저장의 실제 소유자
        ReflectionTestUtils.setField(owner, "id", userId);

        User requestUser = User.builder().build();
        ReflectionTestUtils.setField(requestUser, "id", userId);

        Doc doc = Doc.builder().user(owner).build();
        Branch branch = Branch.builder().doc(doc).build();
        Save save = Save.builder().branch(branch).build();

        when(userRepository.findById(dto.userId())).thenReturn(Optional.of(requestUser));
        when(documentRepository.findById(dto.documentId())).thenReturn(Optional.of(doc));
        when(saveRepository.findSaveById(dto.saveId())).thenReturn(save);

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(SaveErrorCode.SAVE_NOT_OWNER);
                });
    }

}