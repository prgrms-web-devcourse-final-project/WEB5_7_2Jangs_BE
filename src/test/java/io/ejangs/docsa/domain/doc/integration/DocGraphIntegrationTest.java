package io.ejangs.docsa.domain.doc.integration;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.security.WithCustomMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WithCustomMockUser
@AutoConfigureMockMvc(addFilters = false)
@Transactional
public class DocGraphIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocRepository docRepository;

    @Autowired
    private SaveContentRepository saveContentRepository;

    @Autowired
    private CommitBlockSequenceRepository commitBlockSequenceRepository;

    @Autowired
    private BlockRepository blockRepository;


    @Test
    @WithCustomMockUser
    @DisplayName("그래프 조회 정상")
    void createDocAndCommitsAndSave_thenGraphReflectsAll() throws Exception {
        // given
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        userRepository.save(user);

        Doc doc = DocTestUtils.createForkedBranchScenario(user, saveContentRepository,
                commitBlockSequenceRepository, blockRepository);
        docRepository.save(doc);

        // when & then
        mockMvc.perform(get("/api/document/" + doc.getId() + "/graph")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("브랜치 2개 있는 문서임당"))
                .andExpect(jsonPath("$.branches").isArray())
                .andExpect(jsonPath("$.branches.length()").value(1))
                .andExpect(jsonPath("$.commits").isArray())
                .andExpect(jsonPath("$.commits.length()").value(4))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges.length()").value(3));
    }
}
