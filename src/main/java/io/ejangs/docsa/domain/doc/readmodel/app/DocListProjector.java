package io.ejangs.docsa.domain.doc.readmodel.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.doc.readmodel.dao.mongodb.DocListReadModelRepository;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocActivityChangedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocCreatedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocDeletedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocThumbnailChangedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocTitleChangedPayload;
import io.ejangs.docsa.global.outbox.event.dto.DomainEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocListProjector {

    private final DocListReadModelRepository docListReadModelRepository;
    private final ObjectMapper objectMapper;

    public void project(DomainEventMessage message) {
        switch (message.eventType()) {
            case DOC_CREATED -> create(message);
            case DOC_TITLE_CHANGED -> changeTitle(message);
            case DOC_ACTIVITY_CHANGED -> changeActivity(message);
            case DOC_THUMBNAIL_CHANGED -> changeThumbnail(message);
            case DOC_DELETED -> delete(message);
        }
    }

    private <T> T readPayload(DomainEventMessage message, Class<T> payloadType) {
        try {
            return objectMapper.readValue(message.payload(), payloadType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Domain event payload deserialize failed", e);
        }
    }

    private DocListReadModel getRequiredModel(Long docId, DomainEventMessage message) {
        return docListReadModelRepository.findById(docId)
                .orElseThrow(() -> new IllegalStateException(
                        "Doc list read model is missing. eventType=%s, eventId=%d, docId=%d"
                                .formatted(message.eventType(), message.eventId(), docId)
                ));
    }

    private void create(DomainEventMessage message) {
        DocCreatedPayload payload = readPayload(message, DocCreatedPayload.class);

        if (docListReadModelRepository.existsById(payload.docId())) {
            return;
        }

        docListReadModelRepository.save(DocListReadModel.create(payload, message.eventId()));
    }

    private void changeTitle(DomainEventMessage message) {
        DocTitleChangedPayload payload = readPayload(message, DocTitleChangedPayload.class);
        DocListReadModel model = getRequiredModel(payload.docId(), message);

        if (model.changeTitle(payload, message.eventId())) {
            docListReadModelRepository.save(model);
        }
    }

    private void changeActivity(DomainEventMessage message) {
        DocActivityChangedPayload payload = readPayload(message, DocActivityChangedPayload.class);
        DocListReadModel model = getRequiredModel(payload.docId(), message);

        if (model.changeActivity(payload, message.eventId())) {
            docListReadModelRepository.save(model);
        }
    }

    private void changeThumbnail(DomainEventMessage message) {
        DocThumbnailChangedPayload payload = readPayload(message, DocThumbnailChangedPayload.class);
        DocListReadModel model = getRequiredModel(payload.docId(), message);

        if (model.changeThumbnail(payload, message.eventId())) {
            docListReadModelRepository.save(model);
        }
    }

    private void delete(DomainEventMessage message) {
        DocDeletedPayload payload = readPayload(message, DocDeletedPayload.class);
        DocListReadModel model = getRequiredModel(payload.docId(), message);

        if (model.markDeleted(message.eventId())) {
            docListReadModelRepository.save(model);
        }
    }
}
