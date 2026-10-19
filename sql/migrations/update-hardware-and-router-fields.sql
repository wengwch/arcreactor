-- Run once on existing databases created with the previous sql/ scripts.
-- Apply earlier table/column rename migrations first when applicable.
-- Stop application instances and select the application database before migration.
-- New installations should use the top-level sql/*.sql files directly.

ALTER TABLE gpu_type
  ADD COLUMN pci_passthrough_alias VARCHAR(255);

ALTER TABLE hypervisor_type
  ADD COLUMN numa_node_cnt INT NOT NULL DEFAULT 2,
  ADD COLUMN socket_cnt INT NOT NULL DEFAULT 2;

-- Original flavor schemas may already contain vram; retain existing values.
SET @flavor_vram_ddl = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'flavor' AND column_name = 'vram'
  ),
  'SELECT 1',
  'ALTER TABLE flavor ADD COLUMN vram INT NOT NULL DEFAULT 0'
);
PREPARE flavor_vram_statement FROM @flavor_vram_ddl;
EXECUTE flavor_vram_statement;
DEALLOCATE PREPARE flavor_vram_statement;

ALTER TABLE sys_router
  ADD COLUMN shared_net_id VARCHAR(255),
  ADD COLUMN os_shared_net_id VARCHAR(255);

-- Recover network references only when the old subnet identifies one network
-- within the router's region. Retain legacy subnet/port columns and their data.
UPDATE sys_router AS router
JOIN (
  SELECT region_id, os_sub_net_id, MIN(id) AS network_id, MIN(os_net_id) AS os_network_id
  FROM sys_network
  WHERE os_sub_net_id IS NOT NULL
  GROUP BY region_id, os_sub_net_id
  HAVING COUNT(*) = 1
) AS network
  ON network.region_id = router.region_id
  AND network.os_sub_net_id = router.os_shared_net_subnet_id
SET router.shared_net_id = network.network_id,
    router.os_shared_net_id = network.os_network_id;

-- Review unmatched routers and populate their network references manually.
SELECT id, region_id, os_shared_net_subnet_id, os_shared_net_port_id
FROM sys_router
WHERE shared_net_id IS NULL OR os_shared_net_id IS NULL;
