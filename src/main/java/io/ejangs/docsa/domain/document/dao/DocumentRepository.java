package io.ejangs.docsa.domain.document.dao;

import io.ejangs.docsa.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {}
