package io.ejangs.docsa.domain.save.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveBlock;
import io.ejangs.docsa.domain.save.dto.SaveIdentifierDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveMapper;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SaveServiceUnitTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DocRepository docRepository;
    @Mock
    private SaveRepository saveRepository;
    @Mock
    private SaveContentRepository saveContentRepository;
    @Mock
    private Save mockSave;
    @Mock
    private Doc mockDoc;
    @Mock
    private User mockUser;
    @Mock
    private Branch mockBranch;
    @Mock
    private SaveContent mockSaveContent;
    @InjectMocks
    private SaveService saveService;

    private SaveIdentifierDto idDto;
    private List<SaveBlock> data;
    private SaveUpdateRequest request;

    private Long userId = 1L;
    private Long docId = 2L;
    private Long saveId = 3L;

    @BeforeEach
    void setUp() {
        idDto = SaveIdentifierDto.of(docId, saveId, userId);
        data = List.of(
                new SaveBlock(Map.of("text1", "Key features")),
                new SaveBlock(Map.of("text2", "Key features"))
        );
        request = new SaveUpdateRequest(data);
    }

    @Test
    @DisplayName("성공적인 getSave")
    void getSave_success() throws Exception {
        SaveGetResponse expectedResponse = new SaveGetResponse(OffsetDateTime.now(), data);
        SaveContent saveContent = SaveContent.builder().content(data).build();

        when(saveRepository.findById(idDto.saveId())).thenReturn(Optional.of(mockSave));
        when(saveContentRepository.findById(mockSave.getSaveMongoId())).thenReturn(
                Optional.of(saveContent));
        when(saveRepository.validateSaveOwnership(idDto.saveId(), idDto.documentId(),
                idDto.userId())).thenReturn(true);

        when(mockSave.getId()).thenReturn(idDto.saveId());
        when(mockSave.getUpdatedAt()).thenReturn(LocalDateTime.now());

        try (MockedStatic<SaveMapper> mockedMapper = mockStatic(SaveMapper.class)) {
            mockedMapper.when(() -> SaveMapper.toSaveGetResponse(mockSave.getUpdatedAt(), data))
                    .thenReturn(expectedResponse);

            SaveGetResponse actualResponse = saveService.getSave(idDto);

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    @DisplayName("문서의 주인이 아닌 사람이 저장 요청 시 예외가 발생한다")
    void getSave_fail_invalidUser() {
        when(saveRepository.findById(idDto.saveId())).thenReturn(Optional.of(mockSave));
        when(saveRepository.validateSaveOwnership(idDto.saveId(), idDto.documentId(),
                idDto.userId())).thenReturn(
                false);

        when(mockSave.getId()).thenReturn(idDto.saveId());
        // when & then
        assertThatThrownBy(() -> saveService.getSave(idDto))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("잘못된 접근입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 저장 ID로 수정 요청 시 예외가 발생한다")
    void getSave_fail_invalidSave() {
        when(saveRepository.findById(idDto.saveId()))
                .thenThrow(new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> saveService.getSave(idDto))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 저장 데이터를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("성공적인 updateSave")
    void updateSave_success() {
        SaveUpdateResponse expectedResponse = new SaveUpdateResponse(OffsetDateTime.now());

        when(saveRepository.findById(idDto.saveId())).thenReturn(Optional.of(mockSave));
        when(saveContentRepository.findById(mockSave.getSaveMongoId())).thenReturn(
                Optional.of(mockSaveContent));
        when(saveRepository.validateSaveOwnership(idDto.saveId(), idDto.documentId(),
                idDto.userId())).thenReturn(true);

        when(mockSave.getId()).thenReturn(idDto.saveId());

        try (MockedStatic<SaveMapper> mockedMapper = mockStatic(SaveMapper.class)) {
            mockedMapper.when(() -> SaveMapper.toSaveUpdateResponse(mockSave))
                    .thenReturn(expectedResponse);

            SaveUpdateResponse actualResponse = saveService.updateSave(idDto, request);

            assertEquals(expectedResponse, actualResponse);
            verify(saveContentRepository).save(mockSaveContent);
            verify(saveRepository).save(mockSave);
        }
    }

    @Test
    @DisplayName("문서의 주인이 아닌 사람이 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidUser() {
        when(saveRepository.findById(idDto.saveId())).thenReturn(Optional.of(mockSave));
        when(saveRepository.validateSaveOwnership(idDto.saveId(), idDto.documentId(),
                idDto.userId())).thenReturn(
                false);
        when(mockSave.getId()).thenReturn(idDto.saveId());

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(idDto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("잘못된 접근입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 저장 ID로 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidSave() {
        when(saveRepository.findById(idDto.saveId()))
                .thenThrow(new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(idDto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 저장 데이터를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("소유자가 아닌 유저가 save를 수정하려 할 경우 예외 발생")
    void updateSave_throwsException_ifNotOwner() {
        // given
        SaveIdentifierDto dto = SaveIdentifierDto.of(userId, docId, saveId);
        SaveUpdateRequest request = new SaveUpdateRequest(List.of());

        when(saveRepository.findById(dto.saveId())).thenReturn(Optional.of(mockSave));

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(SaveErrorCode.SAVE_NOT_OWNER);
                });
    }

    @Test
    @DisplayName("save가 요청한 document에 속하지 않은 경우 예외 발생")
    void updateSave_throwsException_ifSaveDoesNotBelongToDocument() {
        // given
        Long requestedDocumentId = 999L; // 요청한 문서 ID (실제와 다르게)

        SaveIdentifierDto dto = SaveIdentifierDto.of(requestedDocumentId, saveId, userId);
        SaveUpdateRequest request = new SaveUpdateRequest(List.of());

        when(saveRepository.findById(dto.saveId())).thenReturn(Optional.of(mockSave));
        when(saveRepository.validateSaveOwnership(dto.saveId(), dto.documentId(),
                dto.userId())).thenThrow(new CustomException(SaveErrorCode.SAVE_NOT_OWNER));

        when(mockSave.getId()).thenReturn(dto.saveId());

        // when & then
        assertThatThrownBy(() -> saveService.updateSave(dto, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(SaveErrorCode.SAVE_NOT_OWNER);
                });
    }

}