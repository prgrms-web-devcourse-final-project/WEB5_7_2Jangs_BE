package io.ejangs.docsa.domain.save.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.save.dao.SaveRepository;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaveServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private SaveRepository saveRepository;
    @Mock
    private Branch mockBranch;
    @Mock
    private Document mockDocument;
    @Mock
    private User mockUser;
    @InjectMocks
    private SaveService saveService;

    @Test
    @DisplayName("수정 시 updatedAt이 갱신되는지 확인")
    void updateSave_success() {
        SaveUpdateIdDto dto = SaveUpdateIdDto.of(1L, 1L, 1L);
        SaveUpdateRequest request = new SaveUpdateRequest("new content");

        Save save = Save.builder()
                .branch(mockBranch)
                .content("old content")
                .build();

        when(userRepository.findById(dto.userId())).thenReturn(Optional.of(mockUser));
        when(documentRepository.findById(dto.documentId())).thenReturn(Optional.of(mockDocument));
        when(saveRepository.findById(dto.saveId())).thenReturn(Optional.of(save));

        saveService.updateSave(dto, request);
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
        when(documentRepository.findById(dto.documentId())).thenReturn(Optional.of(mockDocument));

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 저장 데이터를 찾을 수 없습니다.");
    }

}