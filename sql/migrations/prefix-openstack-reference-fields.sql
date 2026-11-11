-- Run once against the previous schema, including add-instance-port-qos.sql.
-- New installations use the current sql/openstack.sql and sql/read-model.sql instead.
-- This only renames relational columns; it does not convert actor events or snapshots.
ALTER TABLE openstack_cluster
  RENAME COLUMN admin_default_project_id TO os_admin_default_project_id,
  RENAME COLUMN admin_default_domain_id TO os_admin_default_domain_id,
  RENAME COLUMN admin_default_region TO os_admin_default_region,
  RENAME COLUMN member_role_id TO os_member_role_id,
  RENAME COLUMN admin_role_id TO os_admin_role_id;

ALTER TABLE user_cluster_project
  RENAME COLUMN project_id TO os_project_id;

ALTER TABLE instance_view
  RENAME COLUMN parent_network_id TO os_parent_network_id,
  RENAME COLUMN parent_subnet_id TO os_parent_subnet_id,
  RENAME COLUMN sub_network_id TO os_sub_network_id,
  RENAME COLUMN sub_subnet_id TO os_sub_subnet_id,
  RENAME COLUMN sub_floating_network_id TO os_sub_floating_network_id,
  RENAME COLUMN nova_server_id TO os_nova_server_id,
  RENAME COLUMN security_group_id TO os_security_group_id,
  RENAME COLUMN parent_port_id TO os_parent_port_id,
  RENAME COLUMN parent_qos_policy_id TO os_parent_qos_policy_id,
  RENAME COLUMN parent_qos_ingress_rule_id TO os_parent_qos_ingress_rule_id,
  RENAME COLUMN parent_qos_egress_rule_id TO os_parent_qos_egress_rule_id,
  RENAME COLUMN sub_port_id TO os_sub_port_id,
  RENAME COLUMN sub_qos_policy_id TO os_sub_qos_policy_id,
  RENAME COLUMN sub_qos_ingress_rule_id TO os_sub_qos_ingress_rule_id,
  RENAME COLUMN sub_qos_egress_rule_id TO os_sub_qos_egress_rule_id,
  RENAME COLUMN sub_floating_ip_id TO os_sub_floating_ip_id,
  RENAME COLUMN trunk_id TO os_trunk_id;

ALTER TABLE port_forwarding
  RENAME COLUMN internal_port_id TO os_internal_port_id,
  RENAME COLUMN tcp_forwarding_id TO os_tcp_forwarding_id,
  RENAME COLUMN udp_forwarding_id TO os_udp_forwarding_id;
