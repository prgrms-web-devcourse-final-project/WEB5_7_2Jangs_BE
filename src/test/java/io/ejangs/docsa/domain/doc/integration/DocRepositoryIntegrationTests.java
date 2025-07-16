package io.ejangs.docsa.domain.doc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class DocRepositoryIntegrationTests {

    @Autowired
    private DocRepository docRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocService docService;

    @Test
    @DisplayName("사이드바 문서리스트 조회")
    void getSimpleDocumentList() throws Exception {
        //given
        User user = DocTestUtils.createUser();
        userRepository.save(user);

        List<Doc> docList = DocTestUtils.createDocumentList(5, user);
        docRepository.saveAll(docList);

        //when
        List<DocListSimpleResponse> results = docService.getSimpleList(
                user.getId());

        //then
        assertEquals(5, results.size());
        assertEquals("테스트 문서 5", results.getFirst().title());
    }
}
