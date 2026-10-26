-- Run once on existing databases before deploying the running VM count field.
-- New installations should use sql/hypervisors.sql directly.
-- Counts are maintained by the hypervisor actor projection.
ALTER TABLE hypervisor_host
  ADD COLUMN running_vms INT NOT NULL DEFAULT 0;
