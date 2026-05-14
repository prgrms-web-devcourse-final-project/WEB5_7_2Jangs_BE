package io.ejangs.docsa.domain.doc.app.create;

import io.ejangs.docsa.domain.branch.app.BranchWriter;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.readmodel.util.DocPayloadFactory;
import io.ejangs.docsa.domain.doc.thumbnail.dao.ThumbnailRepository;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import io.ejangs.docsa.domain.doc.util.DocMapper;
import io.ejangs.docsa.domain.save.app.SaveWriter;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxPublisher;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocCreateMySqlTxService {

    private final DocReader docReader;
    private final BranchWriter branchWriter;
    private final SaveWriter saveWriter;
    private final ThumbnailRepository thumbnailRepository;

    private final DomainEventOutboxPublisher domainEventOutboxPublisher;

    @Value("${default.branch}")
    private String defaultBranchName;

    @Transactional(rollbackFor = Exception.class)
    public DocCreateResponse createMySqlPart(String title, User user,
            String saveContentId) {

        Doc doc = docReader.create(user, title);
        Branch defaultBranch = branchWriter.createBranch(doc, defaultBranchName);
        Save defaultSave = saveWriter.createSave(defaultBranch, saveContentId);
        RenewUpdatedAtHelper.touch(defaultSave);
        thumbnailRepository.save(Thumbnail.builder()
                .doc(doc)
                .build());

        domainEventOutboxPublisher.publish(DomainEventType.DOC_CREATED, AggregateType.DOC,
                doc.getId(), DocPayloadFactory.created(doc, user.getId(), defaultSave.getId()));

        return DocMapper.toCreateResponse(doc, defaultSave);
    }


}
