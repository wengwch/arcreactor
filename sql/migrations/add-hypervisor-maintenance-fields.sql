-- Run once on existing databases before deploying the maintenance fields.
-- New installations should use sql/hypervisors.sql directly.
-- Existing hosts receive MAINTENANCE; no maintenance time is scheduled by default.
ALTER TABLE hypervisor_host
  ADD COLUMN hypervisor_status VARCHAR(32) NOT NULL DEFAULT 'MAINTENANCE',
  ADD COLUMN expected_maintenance_time DATETIME(6);
