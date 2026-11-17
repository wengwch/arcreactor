-- Run once if floating_ip was created before the optional domain was introduced.
-- New installations should use sql/floating-ips.sql directly.
ALTER TABLE floating_ip
  ADD COLUMN domain VARCHAR(255) NULL AFTER floating_ip;
