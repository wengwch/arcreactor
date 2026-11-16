-- Run once if floating_ip was created before port_mask was introduced.
-- New installations should use sql/floating-ips.sql directly.
ALTER TABLE floating_ip
  ADD COLUMN port_mask TEXT;

UPDATE floating_ip SET port_mask = '' WHERE port_mask IS NULL;

ALTER TABLE floating_ip
  MODIFY COLUMN port_mask TEXT NOT NULL;
