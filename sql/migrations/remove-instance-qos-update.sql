-- Run once for an existing instance_view table with the legacy qos_update column.
-- Deploy the application without reads/writes of this column before removing it.
-- Preserve the rate columns, instance_qos_record, instance_current_qos, journals and projection offsets.
-- New installations use sql/read-model.sql.
ALTER TABLE instance_view
  DROP COLUMN qos_update;
