-- Run once for an existing instance_view table with the legacy nova_operation_id column.
-- Deploy the application without reads/writes of this column before removing it.
-- New installations use sql/read-model.sql.
ALTER TABLE instance_view
  DROP COLUMN nova_operation_id;
