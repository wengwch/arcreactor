-- Initialize the floating IP table before using the creation API.
-- CREATE TABLE IF NOT EXISTS does not alter an existing table.
CREATE TABLE IF NOT EXISTS floating_ip (
  id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  region_id VARCHAR(255) NOT NULL,
  ext_net_id VARCHAR(255) NOT NULL,
  os_ext_net_id VARCHAR(255) NOT NULL,
  os_project_id VARCHAR(255) NOT NULL,
  os_floating_ip_id VARCHAR(255) NOT NULL,
  floating_ip VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  port_mask BLOB NOT NULL,
  resource_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_floating_ip_region_os_id (region_id, os_floating_ip_id),
  INDEX idx_floating_ip_ext_net (ext_net_id)
) ENGINE=InnoDB;
