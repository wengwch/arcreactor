-- Run once if hypervisor_host still contains the removed availability zone column.
-- New installations should use sql/hypervisors.sql directly.
ALTER TABLE hypervisor_host
  DROP COLUMN availability_zone;
