-- Run once for an existing instance_view table; new installations use sql/read-model.sql.
ALTER TABLE instance_view
  ADD COLUMN parent_max_kbps INT AFTER os_parent_qos_egress_rule_id,
  ADD COLUMN parent_max_burst_kbits INT AFTER parent_max_kbps,
  ADD COLUMN sub_max_kbps INT AFTER os_sub_qos_egress_rule_id,
  ADD COLUMN sub_max_burst_kbits INT AFTER sub_max_kbps,
  ADD COLUMN user_forwardings JSON AFTER os_trunk_id,
  ADD COLUMN qos_update JSON AFTER user_forwardings;
