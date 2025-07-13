package io.ejangs.docsa.domain.save.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.doc.dao.mysql.DocumentRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.SaveRepositoryAdapter;
import io.ejangs.docsa.domain.save.dao.SaveRepositoryAdapterImpl;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private SaveContent mockSaveContent;
    @InjectMocks
    private SaveService saveService;

    @Test
    @DisplayName("성공적인 updateSave")
    void updateSave_success() {
        // given
        SaveUpdateIdDto dto = SaveUpdateIdDto.of(1L, 1L, 1L);
        SaveUpdateRequest request = new SaveUpdateRequest("content");

        when(userRepository.findById(dto.userId())).thenReturn(Optional.of(mockUser));
        when(documentRepository.findById(dto.documentId())).thenReturn(Optional.of(mockDoc));
        when(saveRepository.findSaveById(dto.saveId())).thenReturn(mockSave);
        when(saveRepository.findSaveContentById(mockSave.getSaveMongoId())).thenReturn(
                mockSaveContent);

        saveService.updateSave(dto, request);

        verify(saveRepository).updateSave(mockSave, mockSaveContent, "content");
    }

    @Test
    @DisplayName("존재하지 않는 유저 ID로 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidUser() {
        // given
        SaveUpdateIdDto dto = SaveUpdateIdDto.of(1L, 1L, -1L);
        SaveUpdateRequest request = new SaveUpdateRequest("content");

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 문서 ID로 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidDocument() {
        // given
        SaveUpdateIdDto dto = SaveUpdateIdDto.of(-1L, 1L, 1L);
        SaveUpdateRequest request = new SaveUpdateRequest("content");

        when(userRepository.findById(dto.userId())).thenReturn(Optional.of(mockUser));

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 문서를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 저장 ID로 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidSave() {
        // given
        SaveUpdateIdDto dto = SaveUpdateIdDto.of(1L, -1L, 1L);
        SaveUpdateRequest request = new SaveUpdateRequest("content");

        when(userRepository.findById(dto.userId())).thenReturn(Optional.of(mockUser));
        when(documentRepository.findById(dto.documentId())).thenReturn(Optional.of(mockDoc));
        when(saveRepository.findSaveById(dto.saveId()))
                .thenThrow(new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 저장 데이터를 찾을 수 없습니다.");
    }

}