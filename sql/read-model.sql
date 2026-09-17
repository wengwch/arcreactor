CREATE TABLE IF NOT EXISTS hypervisor_resource (
  hypervisor_id VARCHAR(255) PRIMARY KEY,
  status VARCHAR(32) NOT NULL,
  availability_zone VARCHAR(255) NOT NULL DEFAULT 'default',
  traits JSON NOT NULL,
  total_vcpu INT NOT NULL DEFAULT 0,
  reserved_vcpu INT NOT NULL DEFAULT 0,
  allocated_vcpu INT NOT NULL DEFAULT 0,
  available_vcpu INT GENERATED ALWAYS AS (total_vcpu - reserved_vcpu - allocated_vcpu) STORED,
  total_memory_mb BIGINT NOT NULL DEFAULT 0,
  reserved_memory_mb BIGINT NOT NULL DEFAULT 0,
  allocated_memory_mb BIGINT NOT NULL DEFAULT 0,
  available_memory_mb BIGINT GENERATED ALWAYS AS
    (total_memory_mb - reserved_memory_mb - allocated_memory_mb) STORED,
  total_gpu INT NOT NULL DEFAULT 0,
  reserved_gpu INT NOT NULL DEFAULT 0,
  allocated_gpu INT NOT NULL DEFAULT 0,
  available_gpu INT GENERATED ALWAYS AS (total_gpu - reserved_gpu - allocated_gpu) STORED,
  resource_version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_hypervisor_candidate (
    status, availability_zone, available_vcpu, available_memory_mb, available_gpu)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS instance_allocation (
  instance_id VARCHAR(255) PRIMARY KEY,
  hypervisor_id VARCHAR(255) NOT NULL,
  reservation_id VARCHAR(255) NOT NULL,
  vcpus INT NOT NULL,
  memory_mb BIGINT NOT NULL,
  gpu_count INT NOT NULL,
  allocation_status VARCHAR(32) NOT NULL,
  resource_version BIGINT NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
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

CREATE TABLE IF NOT EXISTS hypervisor_gpu (
  hypervisor_id VARCHAR(255) NOT NULL,
  gpu_id VARCHAR(255) NOT NULL,
  model VARCHAR(255) NOT NULL,
  memory_mb BIGINT,
  numa_node INT,
  traits JSON NOT NULL,
  assignment_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  reservation_id VARCHAR(255),
  instance_id VARCHAR(255),
  resource_version BIGINT NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (hypervisor_id, gpu_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS pekko_projection_offset_store (
  projection_name VARCHAR(255) NOT NULL,
  projection_key VARCHAR(255) NOT NULL,
  current_offset VARCHAR(255) NOT NULL,
  manifest VARCHAR(255) NOT NULL,
  mergeable BOOLEAN NOT NULL,
  last_updated BIGINT NOT NULL,
  PRIMARY KEY (projection_name, projection_key),
  INDEX projection_name_index (projection_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS pekko_projection_management (
  projection_name VARCHAR(255) NOT NULL,
  projection_key VARCHAR(255) NOT NULL,
  paused BOOLEAN NOT NULL,
  last_updated BIGINT NOT NULL,
  PRIMARY KEY (projection_name, projection_key)
) ENGINE=InnoDB;
