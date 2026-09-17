-- Run once for existing flavor tables with the old local_disk column.
-- If using the original openstack_flavor table, run rename-openstack-tables.sql first.
-- New installations should use sql/openstack.sql directly.
-- Legacy type, vram and cpu_spec columns are retained to avoid discarding data.
ALTER TABLE flavor
  CHANGE COLUMN local_disk disk INT NOT NULL DEFAULT 0,
  ADD COLUMN hypervisor_type VARCHAR(255),
  ADD COLUMN cpu_type VARCHAR(255),
  ADD COLUMN ram_type VARCHAR(255),
  ADD COLUMN disk_type VARCHAR(255),
  ADD INDEX idx_flavor_region (region_id);
