package io.ejangs.docsa.domain.doc.readmodel.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.doc.readmodel.dao.mongodb.DocListReadModelRepository;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.readmodel.dto.DocListPayload;
import io.ejangs.docsa.global.outbox.event.dto.DomainEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocListProjector {

    private final DocListReadModelRepository docListReadModelRepository;
    private final ObjectMapper objectMapper;

    public void project(DomainEventMessage message) {
        DocListPayload payload = readPayload(message);

        switch (message.eventType()) {
            case DOC_CREATED -> docListReadModelRepository.save(DocListReadModel.create(payload, message.eventId()));
            case DOC_TITLE_CHANGED, DOC_ACTIVITY_CHANGED, DOC_THUMBNAIL_CHANGED -> upsert(payload, message.eventId());
            case DOC_DELETED -> markDeleted(payload.docId(), message.eventId());
        }
    }

    private DocListPayload readPayload(DomainEventMessage message) {
        try {
            return objectMapper.readValue(message.payload(), DocListPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Domain event payload deserialize failed", e);
        }
    }

    private void upsert(DocListPayload payload, Long eventId) {
        DocListReadModel model = docListReadModelRepository.findById(payload.docId())
                .orElseGet(() -> DocListReadModel.create(payload, eventId));

        if (model.getLastProjectedEventId() != null && model.getLastProjectedEventId() >= eventId) {
            return;
        }

        model.apply(payload, eventId);
        docListReadModelRepository.save(model);
    }

    private void markDeleted(Long docId, Long eventId) {
        docListReadModelRepository.findById(docId)
                .ifPresent(model -> {
                    model.markDeleted(eventId);
                    docListReadModelRepository.save(model);
                });
    }
}
