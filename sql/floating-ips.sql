-- Apply once before using the floating IP creation API, for new and existing databases.
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
  port_mask BLOB NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_floating_ip_region_os_id (region_id, os_floating_ip_id),
  INDEX idx_floating_ip_ext_net (ext_net_id)
) ENGINE=InnoDB;
