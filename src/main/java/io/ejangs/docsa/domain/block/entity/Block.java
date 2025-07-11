package io.ejangs.docsa.domain.block.entity;

import io.ejangs.docsa.domain.document.entity.Document;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "blocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "unique_id")
    private String uniqueId;

    @Column(nullable = false)
    private String type;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String data;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String tunes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Builder
    private Block(String uniqueId, String type, String data, Document document) {
        this.uniqueId = uniqueId;
        this.type = type;
        this.data = data;
        this.document = document;
    }
}
