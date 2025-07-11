package io.ejangs.docsa.domain.document.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ejangs.docsa.domain.document.app.DocumentService;
import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.dto.DocumentCreateRequest;
import io.ejangs.docsa.domain.document.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.document.util.DocumentTestUtils;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.entity.dao.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
@Transactional
public class DocumentServiceIntegrationTests {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("문서 저장 성공 테스트")
    void documentCreateSuccess() throws Exception {
        // given
        User user = userRepository.save(DocumentTestUtils.createUser());

        DocumentCreateRequest request = new DocumentCreateRequest("테스트 문서");

        // when
        DocumentCreateResponse response = documentService.create(request, user.getId());

        // then
        Document saved = documentRepository.findById(response.id()).orElseThrow();
        assertEquals("테스트 문서", saved.getTitle());
        assertEquals(user.getId(), saved.getUser().getId()); // 유저 연결까지 확인
    }

    @Test
    @DisplayName("문서 생성 실패 - 존재하지 않는 사용자 ID")
    void documentCreateFailTestNotFoundUser() {
        // given
        Long nonexistentUserId = 9999L; // 실제 DB에 없는 ID
        DocumentCreateRequest request = new DocumentCreateRequest("없는 유저 문서");

        // when & then
        CustomException ex = assertThrows(CustomException.class, () ->
                documentService.create(request, nonexistentUserId)
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }
}