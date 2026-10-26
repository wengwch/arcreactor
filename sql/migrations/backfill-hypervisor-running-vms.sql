-- Run once before deploying actor-managed running VM counts; stop application instances first.
-- Requires the existing instance_allocation table and hypervisor_host.running_vms column.
-- New installations use the current read-model.sql and hypervisors.sql directly.
-- Preserve actor journals, snapshots and projection offsets.
ALTER TABLE instance_allocation
  ADD INDEX idx_allocation_hypervisor_status (hypervisor_id, allocation_status);

-- Replace previously synced Nova counts with the actor's confirmed allocations.
UPDATE hypervisor_host AS host
LEFT JOIN (
  SELECT hypervisor_id, COUNT(*) AS running_vms
  FROM instance_allocation
  WHERE allocation_status = 'ALLOCATED'
  GROUP BY hypervisor_id
) AS allocation_count ON allocation_count.hypervisor_id = host.id
SET host.running_vms = COALESCE(allocation_count.running_vms, 0);
