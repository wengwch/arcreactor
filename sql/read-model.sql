CREATE TABLE IF NOT EXISTS user_region_project (
  user_id VARCHAR(255) NOT NULL,
  region_id VARCHAR(255) NOT NULL,
  project_id VARCHAR(255) NOT NULL,
  PRIMARY KEY (user_id, region_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS hypervisor_resource (
  hypervisor_id VARCHAR(255) PRIMARY KEY,
  status VARCHAR(32) NOT NULL,
  availability_zone VARCHAR(255) NOT NULL DEFAULT 'default',
  traits JSON NOT NULL,
  total_vcpu INT NOT NULL DEFAULT 0,
  reserved_vcpu INT NOT NULL DEFAULT 0,
  allocated_vcpu INT NOT NULL DEFAULT 0,
  available_vcpu INT GENERATED ALWAYS AS (total_vcpu - reserved_vcpu - allocated_vcpu) STORED,
  total_memory_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB',
  reserved_memory_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB',
  allocated_memory_gb BIGINT NOT NULL DEFAULT 0 COMMENT 'GB',
  available_memory_gb BIGINT GENERATED ALWAYS AS
    (total_memory_gb - reserved_memory_gb - allocated_memory_gb) STORED,
  total_gpu INT NOT NULL DEFAULT 0,
  reserved_gpu INT NOT NULL DEFAULT 0,
  allocated_gpu INT NOT NULL DEFAULT 0,
  available_gpu INT GENERATED ALWAYS AS (total_gpu - reserved_gpu - allocated_gpu) STORED,
  resource_version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_hypervisor_candidate (
    status, availability_zone, available_vcpu, available_memory_gb, available_gpu)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS instance_allocation (
  instance_id VARCHAR(255) PRIMARY KEY,
  hypervisor_id VARCHAR(255) NOT NULL,
  reservation_id VARCHAR(255) NOT NULL,
  vcpus INT NOT NULL,
  memory_gb BIGINT NOT NULL COMMENT 'GB',
  gpu_count INT NOT NULL,
  allocation_status VARCHAR(32) NOT NULL,
  resource_version BIGINT NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_allocation_hypervisor_status (hypervisor_id, allocation_status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS instance_view (
  instance_id VARCHAR(255) PRIMARY KEY,
  request_id VARCHAR(255),
  phase VARCHAR(64) NOT NULL,
  hypervisor_id VARCHAR(255),
  reservation_id VARCHAR(255),
  nova_operation_id VARCHAR(512),
  nova_server_id VARCHAR(255),
  nova_status VARCHAR(64),
  failure_code VARCHAR(128),
  failure_message TEXT,
  workflow_version BIGINT NOT NULL,
  created_at TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_instance_hypervisor (hypervisor_id),
  INDEX idx_instance_phase (phase),
  INDEX idx_instance_nova_server (nova_server_id)
) ENGINE=InnoDB;
