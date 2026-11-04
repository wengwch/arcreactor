-- Run once before enabling the projection on an existing floating_ip table.
-- New installations should use sql/floating-ips.sql directly.
ALTER TABLE floating_ip
  ADD COLUMN resource_version BIGINT NOT NULL DEFAULT 0;
