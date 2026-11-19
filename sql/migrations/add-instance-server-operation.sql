-- Run once for an existing instance_view table; new installations use sql/read-model.sql.
ALTER TABLE instance_view
  ADD COLUMN server_operation JSON AFTER qos_update;
