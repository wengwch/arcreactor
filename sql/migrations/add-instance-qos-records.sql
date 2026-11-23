-- Initial QoS tables for the active/pending state model; no legacy event or snapshot conversion.
CREATE TABLE IF NOT EXISTS instance_qos_record (
  instance_id VARCHAR(255) NOT NULL,
  operation_id VARCHAR(255) NOT NULL,
  phase VARCHAR(32) NOT NULL COMMENT 'UPDATING, READY, FAILED, EXPIRING, EXPIRY_FAILED, EXPIRED, CANCELLED',
  expires_at TIMESTAMP(6) NOT NULL,
  switched_at TIMESTAMP(6) NULL COMMENT 'Confirmed activation time from InstanceQosUpdated',
  replaced_qos_id VARCHAR(255) NULL COMMENT 'Previously active QoS operation ID',
  parent_max_kbps INT NULL,
  parent_max_burst_kbits INT NULL,
  sub_max_kbps INT NULL,
  sub_max_burst_kbits INT NULL,
  expiry_parent_max_kbps INT NULL,
  expiry_parent_max_burst_kbits INT NULL,
  expiry_sub_max_kbps INT NULL,
  expiry_sub_max_burst_kbits INT NULL,
  failure_message TEXT,
  resource_version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  cancelled_at TIMESTAMP(6) NULL,
  expired_at TIMESTAMP(6) NULL,
  PRIMARY KEY (instance_id, operation_id),
  INDEX idx_instance_qos_history (instance_id, created_at, operation_id)
) ENGINE=InnoDB;

-- The last successfully activated QoS per instance, retained until replacement or confirmed expiry.
-- Rates and saved expiry reset parameters are in instance_qos_record under the same operation_id.
CREATE TABLE IF NOT EXISTS instance_current_qos (
  instance_id VARCHAR(255) NOT NULL PRIMARY KEY,
  operation_id VARCHAR(255) NOT NULL,
  phase VARCHAR(32) NOT NULL COMMENT 'READY, EXPIRING, EXPIRY_FAILED',
  expires_at TIMESTAMP(6) NOT NULL,
  switched_at TIMESTAMP(6) NOT NULL,
  replaced_qos_id VARCHAR(255) NULL,
  resource_version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  INDEX idx_instance_current_qos_expiry (phase, expires_at, instance_id)
) ENGINE=InnoDB;
