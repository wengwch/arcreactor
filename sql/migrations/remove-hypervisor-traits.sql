-- Run once on an existing database before using the mapper without traits.
-- The old NOT NULL column has no default and would reject new capacity rows.
-- New installations use sql/read-model.sql directly.
ALTER TABLE hypervisor_resource DROP COLUMN traits;
