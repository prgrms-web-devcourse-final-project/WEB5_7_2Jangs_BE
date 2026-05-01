ALTER TABLE mongo_delete_outbox
  DROP INDEX uk_mongo_delete_outbox_trigger_domain_origin;

ALTER TABLE mongo_delete_outbox
  DROP COLUMN origin_type;

ALTER TABLE mongo_delete_outbox
  ADD UNIQUE KEY uk_mongo_delete_outbox_trigger_domain_origin (trigger_type, domain_type, origin_id);
