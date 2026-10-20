-- Run once on existing databases before using POST /api/v1/data-sync/hypervisors.
-- New installations should use sql/hypervisors.sql directly.
-- Resolve duplicate (region_id, os_hypervisor_id) values before applying this migration.
-- Hosts without an OpenStack binding should use NULL for os_hypervisor_id.
-- To inspect duplicates:
-- SELECT region_id, os_hypervisor_id, COUNT(*) FROM hypervisor_host
-- WHERE os_hypervisor_id IS NOT NULL
-- GROUP BY region_id, os_hypervisor_id HAVING COUNT(*) > 1;
ALTER TABLE hypervisor_host
  ADD UNIQUE KEY uk_hypervisor_host_region_os_id (region_id, os_hypervisor_id);
