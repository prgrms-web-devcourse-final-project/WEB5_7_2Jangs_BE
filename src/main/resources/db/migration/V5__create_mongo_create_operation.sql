CREATE TABLE mongo_create_operation (
  operation_id varchar(36) NOT NULL,
  user_id bigint NOT NULL,
  operation_type enum('DOC','BRANCH','COMMIT','MERGE') NOT NULL,
  request_hash varchar(64) NOT NULL,
  status enum('PENDING','COMPLETED','COMPENSATING','COMPENSATED','FAILED') NOT NULL,
  mongo_ids json NOT NULL,
  result_entity_id bigint DEFAULT NULL,
  result_save_id bigint DEFAULT NULL,
  completed_at datetime(6) DEFAULT NULL,
  last_error varchar(2000) DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) DEFAULT NULL,
  version bigint DEFAULT NULL,
  PRIMARY KEY (operation_id),
  KEY idx_mongo_create_operation_status_updated_at (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
