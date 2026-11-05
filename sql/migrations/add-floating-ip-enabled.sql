-- Run once if floating_ip was created before enabled was introduced.
-- New installations should use sql/floating-ips.sql directly.
-- Existing rows default to disabled; this script does not infer actor state or backfill historical data.
ALTER TABLE floating_ip
  ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER floating_ip;
