ALTER TABLE mongo_delete_outbox
  MODIFY COLUMN trigger_type enum('COMPENSATE','DELETE') NOT NULL,
  MODIFY COLUMN domain_type enum('BRANCH','COMMIT','DOC','MERGE','SAVE') NOT NULL,
  MODIFY COLUMN status enum('DONE','FAILED','OPEN','PROCESSING') NOT NULL;

CREATE TABLE domain_event_outbox (
  max_retry int NOT NULL,
  retry_count int NOT NULL,
  created_at datetime(6) NOT NULL,
  done_at datetime(6) DEFAULT NULL,
  id bigint NOT NULL AUTO_INCREMENT,
  updated_at datetime(6) DEFAULT NULL,
  version bigint DEFAULT NULL,
  last_error varchar(2000) DEFAULT NULL,
  aggregate_id varchar(255) NOT NULL,
  aggregate_type enum('DOC','BRANCH','COMMIT','SAVE','THUMBNAIL') NOT NULL,
  event_type enum(
    'DOC_CREATED',
    'DOC_TITLE_CHANGED',
    'DOC_ACTIVITY_CHANGED',
    'DOC_THUMBNAIL_CHANGED',
    'DOC_DELETED'
  ) NOT NULL,
  payload json NOT NULL,
  status enum('DONE','FAILED','OPEN','PROCESSING') NOT NULL,
  PRIMARY KEY (id),
  KEY idx_domain_event_outbox_status_created_at (status, created_at),
  KEY idx_domain_event_outbox_status_updated_at (status, updated_at)
);
