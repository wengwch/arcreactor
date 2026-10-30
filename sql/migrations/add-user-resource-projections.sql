-- Apply before deploying the user-resources projection.
-- New installations use sql/read-model.sql, which contains the same definitions.
-- This creates the regional resource tables without changing existing project rows or offsets.
-- The new projection name replays retained User events to populate historical resources.

CREATE TABLE IF NOT EXISTS user_region_share (
  user_id VARCHAR(255) NOT NULL,
  region_id VARCHAR(255) NOT NULL,
  share_id VARCHAR(255) NOT NULL COMMENT 'Logical share ID within the user',
  os_project_id VARCHAR(255),
  name VARCHAR(512) NOT NULL,
  description TEXT,
  size_gib INT NOT NULL COMMENT 'GiB',
  share_proto VARCHAR(32) NOT NULL,
  share_type VARCHAR(255) NOT NULL,
  access_to VARCHAR(512) NOT NULL,
  os_share_id VARCHAR(255),
  os_export_location_id VARCHAR(255),
  export_location_path TEXT,
  os_grant_access_id VARCHAR(255),
  phase VARCHAR(64) NOT NULL,
  attempts INT NOT NULL,
  failure_message TEXT,
  resource_version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (user_id, region_id),
  INDEX idx_user_share_region_phase (region_id, phase),
  INDEX idx_user_share_os_id (region_id, os_share_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_region_network (
  user_id VARCHAR(255) NOT NULL,
  region_id VARCHAR(255) NOT NULL,
  os_project_id VARCHAR(255),
  name VARCHAR(512) NOT NULL,
  ipv4_cidr VARCHAR(255) NOT NULL,
  dns VARCHAR(255),
  gateway VARCHAR(255),
  os_local_network_id VARCHAR(255),
  os_network_id VARCHAR(255),
  os_subnet_id VARCHAR(255),
  phase VARCHAR(64) NOT NULL,
  reconciling BOOLEAN NOT NULL,
  attempts INT NOT NULL,
  failure_message TEXT,
  resource_version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (user_id, region_id),
  INDEX idx_user_network_region_phase (region_id, phase),
  INDEX idx_user_network_os_id (region_id, os_network_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_region_router (
  user_id VARCHAR(255) NOT NULL,
  region_id VARCHAR(255) NOT NULL,
  os_project_id VARCHAR(255),
  os_local_network_id VARCHAR(255),
  os_network_id VARCHAR(255),
  os_subnet_id VARCHAR(255),
  os_router_id VARCHAR(255),
  os_router_port_id VARCHAR(255),
  phase VARCHAR(64) NOT NULL COMMENT 'User network workflow phase',
  reconciling BOOLEAN NOT NULL,
  attempts INT NOT NULL,
  failure_message TEXT,
  resource_version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (user_id, region_id),
  INDEX idx_user_router_region_phase (region_id, phase),
  INDEX idx_user_router_os_id (region_id, os_router_id)
) ENGINE=InnoDB;
