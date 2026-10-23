-- Run once on existing databases before deploying the running VM count field.
-- New installations should use sql/hypervisors.sql directly.
-- Counts are populated by the next hypervisor sync from OpenStack.
ALTER TABLE hypervisor_host
  ADD COLUMN running_vms INT NOT NULL DEFAULT 0;
