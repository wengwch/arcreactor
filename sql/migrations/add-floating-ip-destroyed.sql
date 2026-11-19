-- Legacy boolean layout only; follow with migrate-floating-ip-status.sql.
-- New installations should use sql/floating-ips.sql directly.
-- Retain destroyed rows as versioned tombstones for projection replay.
ALTER TABLE floating_ip
  ADD COLUMN destroyed BOOLEAN NOT NULL DEFAULT FALSE AFTER enabled;
