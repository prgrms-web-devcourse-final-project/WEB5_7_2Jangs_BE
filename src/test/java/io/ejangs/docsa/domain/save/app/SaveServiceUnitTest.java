package io.ejangs.docsa.domain.save.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.doc.thumbnail.app.ThumbnailService;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailSyncResponse;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveIdentifierDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxPublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.RecoverableDataAccessException;

@ExtendWith(MockitoExtension.class)
class SaveServiceUnitTest {

    @Mock
    private SaveQueryService saveQueryService;
    @Mock
    private ThumbnailService thumbnailService;
    @Mock
    private Save mockSave;
    @Mock
    private SaveContent mockSaveContent;
    @Mock
    private DomainEventOutboxPublisher domainEventOutboxPublisher;
    @InjectMocks
    private SaveService saveService;

    private SaveIdentifierDto idDto;
    private List<Map<String, Object>> data;
    private SaveUpdateRequest request;

    private final Long userId = 1L;
    private final Long docId = 2L;
    private final Long saveId = 3L;

    @BeforeEach
    void setUp() {
        idDto = SaveIdentifierDto.of(docId, saveId, userId);
        data = List.of(Map.of("text1", "Key features"), Map.of("text2", "Key features"));
        request = new SaveUpdateRequest(data);
    }

    @Test
    @DisplayName("성공적인 getSave")
    void getSave_success() {
        SaveGetResponse expectedResponse = new SaveGetResponse(LocalDateTime.now(), data);
        SaveContent saveContent = SaveContent.builder().content(data).build();

        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doNothing().when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());
        when(mockSave.getSaveMongoId()).thenReturn("mongo-1");
        when(saveQueryService.getSaveContentById("mongo-1")).thenReturn(saveContent);
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
        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doThrow(new CustomException(SaveErrorCode.SAVE_NOT_OWNER))
                .when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());

        assertThatThrownBy(() -> saveService.getSave(idDto))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(SaveErrorCode.SAVE_NOT_OWNER.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 저장 ID로 조회 요청 시 예외가 발생한다")
    void getSave_fail_invalidSave() {
        when(saveQueryService.getSaveById(idDto.saveId())).thenThrow(
                new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        assertThatThrownBy(() -> saveService.getSave(idDto))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(SaveErrorCode.SAVE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("성공적인 updateSave")
    void updateSave_success() {
        ThumbnailSyncResponse thumbnailSyncResponse = new ThumbnailSyncResponse(10L, "wow",
                ThumbnailStatus.READY);
        SaveUpdateResponse expectedResponse = new SaveUpdateResponse(LocalDateTime.now(),
                thumbnailSyncResponse);

        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doNothing().when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());
        when(mockSave.getSaveMongoId()).thenReturn("mongo-1");
        when(saveQueryService.getSaveContentById("mongo-1")).thenReturn(mockSaveContent);
        when(mockSave.getUpdatedAt()).thenReturn(LocalDateTime.now());
        when(thumbnailService.requestUpdate(idDto.userId(), idDto.documentId()))
                .thenReturn(thumbnailSyncResponse);

        try (MockedStatic<SaveMapper> mockedMapper = mockStatic(SaveMapper.class)) {
            mockedMapper.when(() -> SaveMapper.toSaveUpdateResponse(mockSave.getUpdatedAt(),
                            thumbnailSyncResponse))
                    .thenReturn(expectedResponse);

            SaveUpdateResponse actualResponse = saveService.updateSave(idDto, request);

            assertEquals(expectedResponse, actualResponse);
            verify(saveQueryService).saveSave(mockSave);
            verify(mockSaveContent).updateContent(data);
            verify(saveQueryService).saveSaveContent(mockSaveContent);
        }
    }

    @Test
    @DisplayName("문서의 주인이 아닌 사람이 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidUser() {
        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doThrow(new CustomException(SaveErrorCode.SAVE_NOT_OWNER))
                .when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());

        assertThatThrownBy(() -> saveService.updateSave(idDto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(SaveErrorCode.SAVE_NOT_OWNER.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 저장 ID로 수정 요청 시 예외가 발생한다")
    void updateSave_fail_invalidSave() {
        when(saveQueryService.getSaveById(idDto.saveId())).thenThrow(
                new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        assertThatThrownBy(() -> saveService.updateSave(idDto, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(SaveErrorCode.SAVE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("Mongo 저장 실패 시 저장 실패 예외가 발생한다")
    void updateSave_fail_whenMongoSaveFails() {
        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doNothing().when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());
        when(mockSave.getSaveMongoId()).thenReturn("mongo-1");
        when(saveQueryService.getSaveContentById("mongo-1")).thenReturn(mockSaveContent);
        when(saveQueryService.saveSaveContent(mockSaveContent))
                .thenThrow(new RecoverableDataAccessException("mongo write failed"));

        assertThatThrownBy(() -> saveService.updateSave(idDto, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(SaveErrorCode.FAIL_TO_SAVE));
    }

}
