-- Run with application instances stopped when deploying InstanceProjection without duplicate forwarding writes.
-- Preserve port_forwarding, actor journals/snapshots and projection offsets.
-- New installations no longer create this table.
DROP TABLE IF EXISTS instance_port_forwarding;
