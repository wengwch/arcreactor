CREATE TABLE IF NOT EXISTS openstack_cluster (
  id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  admin_username VARCHAR(255) NOT NULL,
  admin_password VARCHAR(1024) NOT NULL,
  admin_default_project_id VARCHAR(255) NOT NULL,
  admin_default_domain_id VARCHAR(255) NOT NULL,
  admin_default_region VARCHAR(255) NOT NULL,
  auth_endpoint VARCHAR(1024) NOT NULL,
  member_role_id VARCHAR(255),
  admin_role_id VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_openstack_cluster_name (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS openstack_region (
  cluster_id VARCHAR(255) NOT NULL,
  id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  CONSTRAINT openstack_region_cluster_fk
    FOREIGN KEY (cluster_id) REFERENCES openstack_cluster(id) ON DELETE CASCADE,
  INDEX idx_openstack_region_cluster (cluster_id),
  INDEX idx_openstack_region_enabled (cluster_id, enabled)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS openstack_image (
  id VARCHAR(255) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  distro VARCHAR(255),
  version VARCHAR(255),
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  category_id VARCHAR(255),
  gpu_driver_installed BOOLEAN NOT NULL DEFAULT FALSE,
  gpu_driver VARCHAR(255),
  os_image_id VARCHAR(255) NOT NULL,
  region_id VARCHAR(255) NOT NULL,
  FOREIGN KEY (region_id) REFERENCES openstack_region(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS openstack_flavor (
  id VARCHAR(255) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  vcpus INT NOT NULL,
  ram INT NOT NULL,
  local_disk INT NOT NULL DEFAULT 0,
  gpus INT NOT NULL DEFAULT 0,
  type VARCHAR(255),
  vram INT NOT NULL DEFAULT 0,
  gpu_type VARCHAR(255),
  cpu_spec VARCHAR(255),
  os_flavor_id VARCHAR(255) NOT NULL,
  region_id VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY (region_id) REFERENCES openstack_region(id)
) ENGINE=InnoDB;
