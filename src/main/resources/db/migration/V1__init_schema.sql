SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `branches` (
  `created_at` datetime(6) NOT NULL,
  `document_id` bigint DEFAULT NULL,
  `from_commit_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `leaf_commit_id` bigint DEFAULT NULL,
  `merge_target_commit_id` bigint DEFAULT NULL,
  `root_commit_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf6lkgdoc9cekvllr7juun2571` (`name`,`document_id`),
  UNIQUE KEY `UK3xq7lqgyigivvjp137bq1vmqp` (`leaf_commit_id`),
  UNIQUE KEY `UKhr8mfwhefxqiel77vtf8swma0` (`root_commit_id`),
  KEY `FKeltapt1yxecp73acaguheypy` (`document_id`),
  KEY `idx_branches_from_commit_id` (`from_commit_id`),
  KEY `idx_branches_merge_target_commit_id` (`merge_target_commit_id`),
  CONSTRAINT `FK7twh77wcak2w54m32nsy2363` FOREIGN KEY (`root_commit_id`) REFERENCES `commits` (`id`),
  CONSTRAINT `FKa0eeai0ufc7if1gjjnegbx47o` FOREIGN KEY (`leaf_commit_id`) REFERENCES `commits` (`id`),
  CONSTRAINT `FKeltapt1yxecp73acaguheypy` FOREIGN KEY (`document_id`) REFERENCES `docs` (`id`),
  CONSTRAINT `FKmtx5jtry1cln0u3hdlb4m755t` FOREIGN KEY (`merge_target_commit_id`) REFERENCES `commits` (`id`),
  CONSTRAINT `FKs3sjpir7sqlh1i56lo0qkeks9` FOREIGN KEY (`from_commit_id`) REFERENCES `commits` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `commits` (
  `branch_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `commit_mongo_id` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnt8u1dar544lhedn95o9eerl7` (`branch_id`),
  CONSTRAINT `FKnt8u1dar544lhedn95o9eerl7` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `docs` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `title` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_title` (`user_id`,`title`),
  CONSTRAINT `FK9tkihf94m82526acn683ihkdc` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `edges` (
  `document_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `next_commit_id` bigint NOT NULL,
  `prev_commit_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgs0qaalf21ufuu813879c2xc0` (`document_id`),
  KEY `FKnwxobo0keg8te2ynwp094kwqp` (`next_commit_id`),
  KEY `FKq7cxggirphdswwud6pb6y3uki` (`prev_commit_id`),
  CONSTRAINT `FKgs0qaalf21ufuu813879c2xc0` FOREIGN KEY (`document_id`) REFERENCES `docs` (`id`),
  CONSTRAINT `FKnwxobo0keg8te2ynwp094kwqp` FOREIGN KEY (`next_commit_id`) REFERENCES `commits` (`id`),
  CONSTRAINT `FKq7cxggirphdswwud6pb6y3uki` FOREIGN KEY (`prev_commit_id`) REFERENCES `commits` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `images` (
  `created_at` datetime(6) NOT NULL,
  `doc_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `size` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `object_key` varchar(500) NOT NULL,
  `content_type` varchar(255) NOT NULL,
  `original_file_name` varchar(255) NOT NULL,
  `status` enum('ACTIVE','FAILED','PENDING') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_images_object_key` (`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `mongo_delete_outbox` (
  `max_retry` int NOT NULL,
  `retry_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `done_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `last_error` varchar(2000) DEFAULT NULL,
  `origin_id` varchar(255) NOT NULL,
  `domain_type` enum('BRANCH','COMMIT','DOC','MERGE','SAVE') NOT NULL,
  `origin_type` enum('BRANCH_ID','CBS_ID','COMMIT_ID','DOC_ID','SAVE_CONTENT_ID','SAVE_ID') NOT NULL,
  `status` enum('DONE','FAILED','OPEN','PROCESSING') NOT NULL,
  `trigger_type` enum('COMPENSATE','DELETE') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mongo_delete_outbox_trigger_domain_origin` (`trigger_type`,`domain_type`,`origin_type`,`origin_id`),
  KEY `idx_mongo_delete_outbox_status_created_at` (`status`,`created_at`),
  KEY `idx_mongo_delete_outbox_status_updated_at` (`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `mongo_outbox_block_ids` (
  `outbox_id` bigint NOT NULL,
  `block_id` varchar(255) DEFAULT NULL,
  KEY `fk_mongo_outbox_block_ids_outbox` (`outbox_id`),
  CONSTRAINT `fk_mongo_outbox_block_ids_outbox` FOREIGN KEY (`outbox_id`) REFERENCES `mongo_delete_outbox` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `mongo_outbox_commit_ids` (
  `outbox_id` bigint NOT NULL,
  `commit_id` varchar(255) DEFAULT NULL,
  KEY `fk_mongo_outbox_commit_ids_outbox` (`outbox_id`),
  CONSTRAINT `fk_mongo_outbox_commit_ids_outbox` FOREIGN KEY (`outbox_id`) REFERENCES `mongo_delete_outbox` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `mongo_outbox_save_ids` (
  `outbox_id` bigint NOT NULL,
  `save_id` varchar(255) DEFAULT NULL,
  KEY `fk_mongo_outbox_save_ids_outbox` (`outbox_id`),
  CONSTRAINT `fk_mongo_outbox_save_ids_outbox` FOREIGN KEY (`outbox_id`) REFERENCES `mongo_delete_outbox` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `saves` (
  `branch_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `save_mongo_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKc6xd7j1suin26hn2oghr6tkvl` (`branch_id`),
  CONSTRAINT `FK9s60khkapgaopi0la134u25a2` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
