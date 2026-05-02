package io.ejangs.docsa.global.outbox.mongo.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.TriggerType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MongoDeleteJobEnqueuerUnitTest {

    @Mock
    private MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    @InjectMocks
    private MongoDeleteJobEnqueuer mongoDeleteJobEnqueuer;

    @Test
    @DisplayName("유니크 충돌 시 재조회하여 기존 outbox를 반환한다")
    void enqueueReturnsExistingOutboxWhenUniqueConflictOccurs() {
        TriggerType triggerType = TriggerType.COMPENSATE;
        DomainType domainType = DomainType.DOC;
        String originId = "race-origin-1";
        MongoIdsDto ids = new MongoIdsDto(List.of("save-1"), List.of(), List.of());

        MongoDeleteOutbox existing = MongoDeleteOutbox.open(
                triggerType,
                domainType,
                originId,
                ids.saveContentsIds(),
                ids.commitBlockSequenceIds(),
                ids.blockIds()
        );
        ReflectionTestUtils.setField(existing, "id", 99L);

        when(mongoDeleteOutboxRepository.findByTriggerTypeAndDomainTypeAndOriginId(
                triggerType,
                domainType,
                originId
        )).thenReturn(java.util.Optional.empty(), java.util.Optional.of(existing));
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(mongoDeleteOutboxRepository)
                .saveAndFlush(any(MongoDeleteOutbox.class));

        MongoDeleteOutbox result = mongoDeleteJobEnqueuer.enqueueDocCreateCompensation(
                originId,
                ids
        );

        assertThat(result.getId()).isEqualTo(99L);
        verify(mongoDeleteOutboxRepository, times(2))
                .findByTriggerTypeAndDomainTypeAndOriginId(
                        triggerType,
                        domainType,
                        originId
                );
        verify(mongoDeleteOutboxRepository).saveAndFlush(any(MongoDeleteOutbox.class));
    }
}
