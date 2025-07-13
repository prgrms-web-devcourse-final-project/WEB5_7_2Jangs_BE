package io.ejangs.docsa.domain.doc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.DocTitleRequest;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
@Transactional
public class DocServiceIntegrationTests {

    @Autowired
    private DocService docService;

    @Autowired
    private DocRepository docRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("문서 저장 성공 테스트")
    void documentCreateSuccess() throws Exception {
        // given
        User user = userRepository.save(DocTestUtils.createUser());

        DocTitleRequest request = new DocTitleRequest("테스트 문서");

        // when
        DocCreateResponse response = docService.create(request, user.getId());

        // then
        Doc saved = docRepository.findById(response.id()).orElseThrow();
        assertEquals("테스트 문서", saved.getTitle());
        assertEquals(user.getId(), saved.getUser().getId()); // 유저 연결까지 확인
    }

    @Test
    @DisplayName("문서 생성 실패 - 존재하지 않는 사용자 ID")
    void documentCreateFailTestNotFoundUser() {
        // given
        Long nonexistentUserId = 9999L; // 실제 DB에 없는 ID
        DocTitleRequest request = new DocTitleRequest("없는 유저 문서");

        // when & then
        CustomException ex = assertThrows(CustomException.class, () ->
                docService.create(request, nonexistentUserId)
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }
}