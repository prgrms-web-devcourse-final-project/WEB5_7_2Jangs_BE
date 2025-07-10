package io.ejangs.docsa.domain.save.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.save.dao.SaveRepository;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SaveServiceIntegrationTest {

    @Autowired
    SaveService saveService;

    @Autowired
    SaveRepository saveRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    DocumentRepository documentRepository;

    @Test
    @DisplayName("updateSave가 내용을 실제로 수정하고 updatedAt을 갱신하는지 검증")
    void updateSave_shouldUpdateContentAndUpdatedAt() {
        // Given: 테스트용 user, document, save 엔티티를 직접 생성
        User user = userRepository.save(User.builder()
                .email("email@gmail.com")
                .name("han")
                .password("password1234")
                .build());
        Document document = documentRepository.save(Document.builder()
                .title("title")
                .user(user)
                .build());
        Save save = saveRepository.save(Save.builder().content("원래 내용").build());

        LocalDateTime beforeUpdate = save.getUpdatedAt();

        // When
        SaveUpdateIdDto dto = SaveUpdateIdDto.of(document.getId(), save.getId(), user.getId());
        SaveUpdateRequest request = new SaveUpdateRequest("수정된 내용");
        LocalDateTime updatedAt = saveService.updateSave(dto, request);

        // Then
        Save updated = saveRepository.findById(save.getId()).orElseThrow();
        assertEquals("수정된 내용", updated.getContent());
        assertTrue(updated.getUpdatedAt().isAfter(beforeUpdate));
        assertEquals(updated.getUpdatedAt(), updatedAt);
    }
}
