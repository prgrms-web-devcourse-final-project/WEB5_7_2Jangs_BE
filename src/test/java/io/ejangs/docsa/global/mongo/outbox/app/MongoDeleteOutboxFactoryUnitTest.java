package io.ejangs.docsa.global.mongo.outbox.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.global.mongo.outbox.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.outbox.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.TriggerType;
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
class MongoDeleteOutboxFactoryUnitTest {

    @Mock
    private MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    @Mock
    private MongoDeleteOutboxCreateService mongoDeleteOutboxCreateService;

    @InjectMocks
    private MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    @Test
    @DisplayName("유니크 충돌 시 재조회하여 기존 outbox를 반환한다")
    void createReturnsExistingOutboxWhenUniqueConflictOccurs() {
        TriggerType triggerType = TriggerType.COMPENSATE;
        DomainType domainType = DomainType.DOC;
        OriginType originType = OriginType.DOC_ID;
        String originId = "race-origin-1";
        MongoIdsDto ids = new MongoIdsDto(List.of("save-1"), List.of(), List.of());

        MongoDeleteOutbox existing = MongoDeleteOutbox.open(
                triggerType,
                domainType,
                originType,
                originId,
                ids.saveContentsIds(),
                ids.commitBlockSequenceIds(),
                ids.blockIds()
        );
        ReflectionTestUtils.setField(existing, "id", 99L);

        when(mongoDeleteOutboxRepository.findByTriggerTypeAndDomainTypeAndOriginTypeAndOriginId(
                triggerType,
                domainType,
                originType,
                originId
        )).thenReturn(java.util.Optional.empty(), java.util.Optional.of(existing));
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(mongoDeleteOutboxCreateService)
                .tryCreate(any(MongoDeleteOutbox.class));

        MongoDeleteOutbox result = mongoDeleteOutboxFactory.create(
                triggerType,
                domainType,
                originType,
                originId,
                ids
        );

        assertThat(result.getId()).isEqualTo(99L);
        verify(mongoDeleteOutboxRepository, times(2))
                .findByTriggerTypeAndDomainTypeAndOriginTypeAndOriginId(
                        triggerType,
                        domainType,
                        originType,
                        originId
                );
        verify(mongoDeleteOutboxCreateService).tryCreate(any(MongoDeleteOutbox.class));
    }
}
