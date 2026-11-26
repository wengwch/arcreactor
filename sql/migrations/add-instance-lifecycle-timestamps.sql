-- Run once for an existing instance_view table; new installations use sql/read-model.sql.
ALTER TABLE instance_view
  ADD COLUMN failed_at TIMESTAMP(6) NULL AFTER failure_message,
  ADD COLUMN deleted_at TIMESTAMP(6) NULL AFTER failed_at;
