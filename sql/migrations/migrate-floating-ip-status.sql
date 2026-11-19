-- Run once on the legacy floating_ip table with enabled and destroyed columns.
-- If either column is missing, apply its add-floating-ip-*.sql migration first.
-- New installations should use sql/floating-ips.sql directly.
ALTER TABLE floating_ip
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DISABLED';

UPDATE floating_ip
SET status = CASE
  WHEN destroyed = TRUE THEN 'DESTROYED'
  WHEN enabled = TRUE THEN 'ENABLED'
  ELSE 'DISABLED'
END;

ALTER TABLE floating_ip
  DROP COLUMN enabled,
  DROP COLUMN destroyed;
