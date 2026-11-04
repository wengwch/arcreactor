-- Run against the application database with application instances stopped.
-- Existing read models already use the GB memory schema; new installations use sql/read-model.sql.
-- Only missing disk columns are added, so an interrupted migration can be run again.
-- Existing resource values, versions and projection offsets are not reset.
-- Newly added disk counters start at zero; this script does not reconstruct resource accounting.
-- If reservations/allocations already exist, rebuild their disk accounting from the actor events
-- before scheduling. Refresh capacity with PUT /api/v1/hypervisor-hosts/{id}/enable afterwards.

DROP PROCEDURE IF EXISTS migrate_disk_resource_fields;
DELIMITER //
CREATE PROCEDURE migrate_disk_resource_fields()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hypervisor_resource' AND column_name = 'total_disk_gb'
  ) THEN
    ALTER TABLE hypervisor_resource
      ADD COLUMN total_disk_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB' AFTER available_memory_gb;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hypervisor_resource' AND column_name = 'reserved_disk_gb'
  ) THEN
    ALTER TABLE hypervisor_resource
      ADD COLUMN reserved_disk_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB' AFTER total_disk_gb;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hypervisor_resource' AND column_name = 'allocated_disk_gb'
  ) THEN
    ALTER TABLE hypervisor_resource
      ADD COLUMN allocated_disk_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB' AFTER reserved_disk_gb;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hypervisor_resource' AND column_name = 'available_disk_gb'
  ) THEN
    ALTER TABLE hypervisor_resource
      ADD COLUMN available_disk_gb BIGINT GENERATED ALWAYS AS
        (total_disk_gb - reserved_disk_gb - allocated_disk_gb) STORED AFTER allocated_disk_gb;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'instance_allocation' AND column_name = 'disk_gb'
  ) THEN
    ALTER TABLE instance_allocation
      ADD COLUMN disk_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB' AFTER memory_gb;
    ALTER TABLE instance_allocation ALTER COLUMN disk_gb DROP DEFAULT;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'instance_view' AND column_name = 'disk_gb'
  ) THEN
    ALTER TABLE instance_view ADD COLUMN disk_gb BIGINT COMMENT 'GB';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'hypervisor_resource'
      AND index_name = 'idx_hypervisor_candidate' AND column_name = 'available_disk_gb'
  ) THEN
    IF EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'hypervisor_resource'
        AND index_name = 'idx_hypervisor_candidate'
    ) THEN
      ALTER TABLE hypervisor_resource DROP INDEX idx_hypervisor_candidate;
    END IF;
    ALTER TABLE hypervisor_resource ADD INDEX idx_hypervisor_candidate (
      status, availability_zone, available_vcpu, available_memory_gb, available_disk_gb, available_gpu);
  END IF;
END//
DELIMITER ;

CALL migrate_disk_resource_fields();
DROP PROCEDURE migrate_disk_resource_fields;

-- Inspect capacity after the projection has consumed the refreshed CapacityUpdated events.
SELECT hypervisor_id, total_disk_gb, reserved_disk_gb, allocated_disk_gb, available_disk_gb
FROM hypervisor_resource;
