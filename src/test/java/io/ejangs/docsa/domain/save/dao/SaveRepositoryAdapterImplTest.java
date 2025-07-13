package io.ejangs.docsa.domain.save.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.util.JsonConverter;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.exception.CustomException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaveRepositoryAdapterImplTest {

    @InjectMocks
    private SaveRepositoryAdapterImpl adapter;

    @Mock
    private SaveRepository saveRepository;
    @Mock
    private SaveContentRepository saveContentRepository;

    @Test
    @DisplayName("findSaveById - 성공")
    void findSaveById_success() {
        Save mockSave = Save.builder().saveMongoId("mongo123").build();
        when(saveRepository.findById(1L)).thenReturn(Optional.of(mockSave));

        Save result = adapter.findSaveById(1L);

        assertThat(result).isEqualTo(mockSave);
    }

    @Test
    @DisplayName("findSaveById - 실패")
    void findSaveById_fail() {
        when(saveRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.findSaveById(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 저장 데이터를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("findSaveContentById - 성공")
    void findSaveContentById_success() {
        SaveContent content = SaveContent.builder()
                .content(Map.of("key", "value"))
                .build();
        when(saveContentRepository.findById("mongo123")).thenReturn(Optional.of(content));

        SaveContent result = adapter.findSaveContentById("mongo123");

        assertThat(result).isEqualTo(content);
    }

    @Test
    @DisplayName("findSaveContentById - 실패")
    void findSaveContentById_fail() {
        when(saveContentRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.findSaveContentById("invalid-id"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 저장 데이터를 찾을 수 없습니다.");
    }


    @Test
    @DisplayName("updateSave - 성공")
    void updateSave_success() {
        Save mockSave = Save.builder().saveMongoId("mongo123").build();
        SaveContent mockContent = SaveContent.builder()
                .content(Map.of("old", "data"))
                .build();
        String newContent = "{\"new\":\"data\"}";

        try (MockedStatic<JsonConverter> jsonConverter = mockStatic(JsonConverter.class)) {
            jsonConverter.when(() -> JsonConverter.toMap(newContent))
                    .thenReturn(Map.of("new", "data"));

            SaveUpdateResponse response = adapter.updateSave(mockSave, mockContent, newContent);

            verify(saveContentRepository).save(mockContent);
            assertThat(response).isNotNull();
        }
    }
}
