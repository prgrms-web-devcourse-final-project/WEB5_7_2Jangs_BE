ALTER TABLE images
  ADD COLUMN purpose enum('DOC_CONTENT','DOC_THUMBNAIL') NOT NULL DEFAULT 'DOC_CONTENT'
  AFTER original_file_name;

ALTER TABLE images
  MODIFY COLUMN status enum('ACTIVE','DELETED','DELETING','FAILED','PENDING') NOT NULL;

SET @idx_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'mongo_delete_outbox'
    AND index_name = 'idx_mongo_delete_outbox_status_created_at'
);
SET @sql = IF(
  @idx_exists = 0,
  'CREATE INDEX idx_mongo_delete_outbox_status_created_at ON mongo_delete_outbox (status, created_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'mongo_delete_outbox'
    AND index_name = 'idx_mongo_delete_outbox_status_updated_at'
);
SET @sql = IF(
  @idx_exists = 0,
  'CREATE INDEX idx_mongo_delete_outbox_status_updated_at ON mongo_delete_outbox (status, updated_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE doc_thumbnails (
  created_at datetime(6) NOT NULL,
  current_image_id bigint DEFAULT NULL,
  doc_id bigint NOT NULL,
  generated_at datetime(6) DEFAULT NULL,
  id bigint NOT NULL AUTO_INCREMENT,
  request_token bigint NOT NULL,
  requested_at datetime(6) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  version bigint DEFAULT NULL,
  last_error varchar(500) DEFAULT NULL,
  signature varchar(255) DEFAULT NULL,
  status enum('EMPTY','FAILED','PENDING','READY') NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_thumbnails_doc_id (doc_id),
  UNIQUE KEY uk_doc_thumbnails_current_image_id (current_image_id),
  CONSTRAINT fk_doc_thumbnails_current_image
    FOREIGN KEY (current_image_id) REFERENCES images (id),
  CONSTRAINT fk_doc_thumbnails_doc
    FOREIGN KEY (doc_id) REFERENCES docs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE s3_delete_outbox (
  max_retry int NOT NULL,
  retry_count int NOT NULL,
  created_at datetime(6) NOT NULL,
  done_at datetime(6) DEFAULT NULL,
  id bigint NOT NULL AUTO_INCREMENT,
  image_id bigint NOT NULL,
  updated_at datetime(6) DEFAULT NULL,
  version bigint DEFAULT NULL,
  object_key varchar(500) NOT NULL,
  last_error varchar(2000) DEFAULT NULL,
  status enum('DONE','FAILED','OPEN','PROCESSING') NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_s3_delete_outbox_object_key (object_key),
  KEY idx_s3_delete_outbox_status_created_at (status, created_at),
  KEY idx_s3_delete_outbox_status_updated_at (status, updated_at),
  CONSTRAINT fk_s3_delete_outbox_image
    FOREIGN KEY (image_id) REFERENCES images (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
