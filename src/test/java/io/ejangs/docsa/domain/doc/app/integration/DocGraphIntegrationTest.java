package io.ejangs.docsa.domain.doc.app.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class DocGraphIntegrationTest {


    @Mock
    private DocRepository docRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SaveContentRepository saveContentRepository;

    @Mock
    private CommitBlockSequenceRepository commitBlockSequenceRepository;

    @Mock
    private BlockRepository blockRepository;

    @InjectMocks
    private DocReader docReader;

    private User user;

    @BeforeEach
    void setup() {
        user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", 1L);

        DocTestUtils.stubSaveMethodsForForkedBranchScenario(blockRepository,
                commitBlockSequenceRepository, saveContentRepository);
    }

    @Test
    @DisplayName("그래프 데이터 가져오기 테스트")
    void testGetDocumentGraph() throws Exception {

        Doc doc = DocTestUtils.createForkedBranchScenario(user, saveContentRepository,
                commitBlockSequenceRepository, blockRepository);

        when(docRepository.findById(doc.getId())).thenReturn(Optional.of(doc));

        Doc result = docReader.getById(doc.getId());

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("브랜치 2개 있는 문서임당");
        assertThat(result.getBranches().size()).isEqualTo(2);
        assertThat(result.getEdges()).hasSize(3);
        long totalCommits =
                result.getBranches().stream().mapToLong(branch -> branch.getCommits().size()).sum();
        assertThat(totalCommits).isEqualTo(4);

    }
}
