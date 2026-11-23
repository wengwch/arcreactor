-- Run once for an existing instance_view table before using the updated instance projection and queries.
-- New installations use sql/read-model.sql. Existing rows are not backfilled.
ALTER TABLE instance_view
  ADD COLUMN hostname VARCHAR(255) NULL AFTER hypervisor_id;
