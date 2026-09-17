CREATE TABLE IF NOT EXISTS sys_network (
  id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  ipv4_cidr VARCHAR(255),
  gateway VARCHAR(255),
  dns VARCHAR(255),
  segment_id VARCHAR(255),
  sys_network_type VARCHAR(255),
  physical_network VARCHAR(255),
  router_externally BOOLEAN NOT NULL DEFAULT FALSE,
  shared BOOLEAN NOT NULL DEFAULT FALSE,
  os_net_id VARCHAR(255),
  os_sub_net_id VARCHAR(255),
  os_network_type VARCHAR(255),
  os_project_id VARCHAR(255),
  region_id VARCHAR(255) NOT NULL,
  host_route JSON NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_sys_network_region (region_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_router (
  id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  ext_net_id VARCHAR(255),
  os_router_id VARCHAR(255),
  os_project_id VARCHAR(255),
  os_ext_net_id VARCHAR(255),
  os_shared_net_subnet_id VARCHAR(255),
  os_shared_net_port_id VARCHAR(255),
  region_id VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_sys_router_region (region_id),
  INDEX idx_sys_router_ext_net (ext_net_id)
) ENGINE=InnoDB;
