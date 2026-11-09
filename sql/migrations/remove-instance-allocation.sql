-- Run with application instances stopped when deploying the projection that updates running_vms directly.
-- Preserve hypervisor_host counters, resource_version and projection offsets.
-- New installations no longer create this table.
DROP TABLE IF EXISTS instance_allocation;
