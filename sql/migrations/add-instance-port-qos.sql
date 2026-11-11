-- Run once for an existing instance_view table before using the updated instance projection.
-- Then apply sql/migrations/prefix-openstack-reference-fields.sql.
-- New installations use sql/read-model.sql.
ALTER TABLE instance_view
  ADD COLUMN parent_qos_policy_id VARCHAR(255),
  ADD COLUMN parent_qos_ingress_rule_id VARCHAR(255),
  ADD COLUMN parent_qos_egress_rule_id VARCHAR(255),
  ADD COLUMN sub_qos_policy_id VARCHAR(255),
  ADD COLUMN sub_qos_ingress_rule_id VARCHAR(255),
  ADD COLUMN sub_qos_egress_rule_id VARCHAR(255);
