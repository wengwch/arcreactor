-- Run once for an existing instance_view table that has the legacy user_forwardings column.
-- Deploy the application without duplicate forwarding reads/writes before removing this column.
-- Preserve port_forwarding, actor journals/snapshots and projection offsets.
ALTER TABLE instance_view
  DROP COLUMN user_forwardings;
