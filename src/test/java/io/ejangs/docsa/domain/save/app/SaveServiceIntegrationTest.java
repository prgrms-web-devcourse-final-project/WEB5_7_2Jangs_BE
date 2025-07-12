package io.ejangs.docsa.domain.save.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ejangs.docsa.domain.doc.dao.mysql.DocumentRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        Doc doc = documentRepository.save(Doc.builder()
                .title("title")
                .user(user)
                .build());
        Save oldSave = saveRepository.save(Save.builder().content("원래 내용").build());

        LocalDateTime beforeUpdate = oldSave.getUpdatedAt();

        // When
        SaveUpdateIdDto dto = SaveUpdateIdDto.of(doc.getId(), oldSave.getId(), user.getId());
        SaveUpdateRequest request = new SaveUpdateRequest("수정된 내용");
        SaveUpdateResponse response = saveService.updateSave(dto, request);

        // Then
        Save updatedSave = saveRepository.findById(oldSave.getId()).orElseThrow();
        assertEquals("수정된 내용", updatedSave.getContent());
        assertTrue(updatedSave.getUpdatedAt().isAfter(beforeUpdate));

        ZoneOffset offset = ZoneOffset.of("+09:00"); // 예: KST 기준

        OffsetDateTime updatedAt = updatedSave.getUpdatedAt().atOffset(offset);

        assertEquals(updatedAt, response.updatedAt());
    }
}
