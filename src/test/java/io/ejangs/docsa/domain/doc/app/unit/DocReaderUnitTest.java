package io.ejangs.docsa.domain.doc.app.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DocReaderUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocRepository docRepository;

    @InjectMocks
    private DocReader docReader;

    @Test
    @DisplayName("문서 생성 성공 - 저장 후 flush하고 유저에 문서를 연결한다")
    void create_success() {
        User user = DocTestUtils.createUser();
        Doc doc = Doc.builder().title("doc").user(user).build();

        when(docRepository.save(org.mockito.ArgumentMatchers.any(Doc.class))).thenReturn(doc);

        Doc result = docReader.create(user, "doc");

        assertThat(result).isEqualTo(doc);
        assertThat(user.getDocs()).contains(doc);
        verify(docRepository).flush();
    }

    @Test
    @DisplayName("문서 생성 실패 - 제목 유니크 제약 충돌이면 TITLE_DUPLICATION을 반환한다")
    void create_fail_duplicateTitleConstraint() {
        User user = DocTestUtils.createUser();
        ConstraintViolationException cause = new ConstraintViolationException(
                "Duplicate entry '1-doc' for key 'docs.uk_user_title'",
                null,
                "docs.uk_user_title");

        when(docRepository.save(org.mockito.ArgumentMatchers.any(Doc.class)))
                .thenThrow(new DataIntegrityViolationException("could not execute statement", cause));

        assertThatThrownBy(() -> docReader.create(user, "doc"))
                .isInstanceOf(CustomException.class)
                .hasMessage(DocErrorCode.TITLE_DUPLICATION.getMessage());
    }
}
