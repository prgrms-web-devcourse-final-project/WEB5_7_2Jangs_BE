package io.ejangs.docsa.domain.save.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveIdentifierDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.mongo.outbox.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.TriggerType;
import io.ejangs.docsa.global.mongo.outbox.app.MongoDeleteOutboxFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private MongoDeleteOutboxFactory mongoDeleteOutboxFactory;
    @Mock
    private Save mockSave;
    @Mock
    private SaveContent mockSaveContent;
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
        SaveUpdateResponse expectedResponse = new SaveUpdateResponse(LocalDateTime.now());

        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doNothing().when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());
        when(mockSave.getSaveMongoId()).thenReturn("mongo-1");
        when(saveQueryService.getSaveContentById("mongo-1")).thenReturn(mockSaveContent);
        when(mockSave.getUpdatedAt()).thenReturn(LocalDateTime.now());

        try (MockedStatic<SaveMapper> mockedMapper = mockStatic(SaveMapper.class)) {
            mockedMapper.when(() -> SaveMapper.toSaveUpdateResponse(mockSave.getUpdatedAt()))
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

    @Test
    @DisplayName("저장 삭제 성공")
    void deleteSave_success() {
        Branch mockBranch = org.mockito.Mockito.mock(Branch.class);
        Commit mockCommit = org.mockito.Mockito.mock(Commit.class);

        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doNothing().when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());
        when(mockSave.getBranch()).thenReturn(mockBranch);
        when(mockBranch.getCommits()).thenReturn(List.of(mockCommit));
        when(mockSave.getSaveMongoId()).thenReturn("mongo-1");
        when(mockSave.getId()).thenReturn(saveId);

        saveService.deleteSave(idDto);

        verify(saveQueryService).deleteSave(mockSave);
        ArgumentCaptor<MongoIdsDto> targetCaptor = ArgumentCaptor.forClass(MongoIdsDto.class);
        verify(mongoDeleteOutboxFactory).create(
                eq(TriggerType.DELETE),
                eq(DomainType.SAVE),
                eq(OriginType.SAVE_ID),
                eq(saveId),
                targetCaptor.capture()
        );
        assertThat(targetCaptor.getValue().saveContentsIds()).containsExactly("mongo-1");
    }

    @Test
    @DisplayName("저장 삭제 실패 - 저장 없음")
    void deleteSave_shouldFail_whenSaveNotFound() {
        when(saveQueryService.getSaveById(idDto.saveId()))
                .thenThrow(new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        assertThatThrownBy(() -> saveService.deleteSave(idDto))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(SaveErrorCode.SAVE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("저장 삭제 실패 - 저장 소유자 아님")
    void deleteSave_shouldFail_whenNotOwner() {
        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doThrow(new CustomException(SaveErrorCode.SAVE_NOT_OWNER))
                .when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());

        assertThatThrownBy(() -> saveService.deleteSave(idDto))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(SaveErrorCode.SAVE_NOT_OWNER.getMessage());
    }

    @Test
    @DisplayName("저장 삭제 실패 - 브랜치에서 커밋이 없는 상태")
    void deleteSave_shouldFail_whenFirstSave_NoCommit() {
        Branch mockBranch = org.mockito.Mockito.mock(Branch.class);

        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doNothing().when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());
        when(mockSave.getBranch()).thenReturn(mockBranch);
        when(mockBranch.getCommits()).thenReturn(List.of());

        assertThatThrownBy(() -> saveService.deleteSave(idDto))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(SaveErrorCode.CANNOT_DELETE_SAVE_WITH_NO_COMMIT.getMessage());
    }

    @Test
    @DisplayName("deleteSave 실패 - Outbox 생성 실패")
    void deleteSave_shouldFail_whenOutboxCreateFails() {
        Branch mockBranch = org.mockito.Mockito.mock(Branch.class);
        Commit mockCommit = org.mockito.Mockito.mock(Commit.class);

        when(saveQueryService.getSaveById(idDto.saveId())).thenReturn(mockSave);
        doNothing().when(saveQueryService)
                .checkSaveAndDocOwner(mockSave, idDto.userId(), idDto.documentId());
        when(mockSave.getBranch()).thenReturn(mockBranch);
        when(mockBranch.getCommits()).thenReturn(List.of(mockCommit));
        when(mockSave.getSaveMongoId()).thenReturn("mongo-1");
        when(mockSave.getId()).thenReturn(saveId);
        doThrow(new RuntimeException("outbox create failed"))
                .when(mongoDeleteOutboxFactory)
                .create(any(), any(), any(), anyLong(), any(MongoIdsDto.class));

        assertThatThrownBy(() -> saveService.deleteSave(idDto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("outbox create failed");

        verify(saveQueryService).deleteSave(mockSave);
    }
}
