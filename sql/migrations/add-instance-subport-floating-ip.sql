-- Run once for an existing instance_view table before using the updated instance projection.
-- New installations use sql/read-model.sql.
ALTER TABLE instance_view
  ADD COLUMN sub_floating_network_id VARCHAR(255),
  ADD COLUMN sub_floating_ip_id VARCHAR(255),
  ADD COLUMN sub_floating_ip_address VARCHAR(45);
