-- Run once on existing read models before deploying the GB memory fields.
-- Stop application instances while migrating; new installations use sql/read-model.sql.
-- Application GB means 1024 MB, matching the existing host/flavor RAM convention.
-- Host/flavor RAM and GPU type VRAM are already in GB and need no conversion.
-- Actor events/snapshots are converted during deserialization; retain journal and offsets.
-- Reject fractional GB values before changing data, so integer fields never truncate memory.

DROP PROCEDURE IF EXISTS migrate_memory_to_gb;
DELIMITER //
CREATE PROCEDURE migrate_memory_to_gb()
BEGIN
  IF EXISTS (
    SELECT 1 FROM hypervisor_resource
    WHERE MOD(total_memory_mb, 1024) <> 0
       OR MOD(reserved_memory_mb, 1024) <> 0
       OR MOD(allocated_memory_mb, 1024) <> 0
  ) OR EXISTS (
    SELECT 1 FROM instance_allocation WHERE MOD(memory_mb, 1024) <> 0
  ) OR EXISTS (
    SELECT 1 FROM hypervisor_gpu WHERE MOD(memory_mb, 1024) <> 0
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Memory values must be divisible by 1024 before converting to whole GB';
  END IF;

  ALTER TABLE hypervisor_resource
    DROP INDEX idx_hypervisor_candidate,
    DROP COLUMN available_memory_mb;

  ALTER TABLE hypervisor_resource
    CHANGE COLUMN total_memory_mb total_memory_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB',
    CHANGE COLUMN reserved_memory_mb reserved_memory_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB',
    CHANGE COLUMN allocated_memory_mb allocated_memory_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB';

  UPDATE hypervisor_resource SET
    total_memory_gb = total_memory_gb DIV 1024,
    reserved_memory_gb = reserved_memory_gb DIV 1024,
    allocated_memory_gb = allocated_memory_gb DIV 1024;

  ALTER TABLE hypervisor_resource
    ADD COLUMN available_memory_gb BIGINT GENERATED ALWAYS AS
      (total_memory_gb - reserved_memory_gb - allocated_memory_gb) STORED
      AFTER allocated_memory_gb,
    ADD INDEX idx_hypervisor_candidate (
      status, availability_zone, available_vcpu, available_memory_gb, available_gpu);

  ALTER TABLE instance_allocation
    CHANGE COLUMN memory_mb memory_gb BIGINT NOT NULL COMMENT 'GB';
  UPDATE instance_allocation SET memory_gb = memory_gb DIV 1024;

  ALTER TABLE hypervisor_gpu
    CHANGE COLUMN memory_mb memory_gb BIGINT COMMENT 'GB';
  UPDATE hypervisor_gpu SET memory_gb = memory_gb DIV 1024;
END//
DELIMITER ;

CALL migrate_memory_to_gb();
DROP PROCEDURE migrate_memory_to_gb;
