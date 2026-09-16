CREATE TABLE IF NOT EXISTS event_journal (
  slice INT NOT NULL,
  entity_type VARCHAR(255) NOT NULL,
  persistence_id VARCHAR(255) NOT NULL,
  seq_nr BIGINT NOT NULL,
  db_timestamp TIMESTAMP(6) NOT NULL,
  event_ser_id INT NOT NULL,
  event_ser_manifest VARCHAR(255) NOT NULL,
  event_payload LONGBLOB NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  writer VARCHAR(255) NOT NULL,
  adapter_manifest VARCHAR(255),
  tags VARCHAR(255),
  meta_ser_id INT,
  meta_ser_manifest VARCHAR(255),
  meta_payload LONGBLOB,
  PRIMARY KEY (persistence_id, seq_nr),
  INDEX event_journal_slice_idx (slice, entity_type, db_timestamp, seq_nr)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS snapshot (
  slice INT NOT NULL,
  entity_type VARCHAR(255) NOT NULL,
  persistence_id VARCHAR(255) PRIMARY KEY,
  seq_nr BIGINT NOT NULL,
  write_timestamp BIGINT NOT NULL,
  ser_id INT NOT NULL,
  ser_manifest VARCHAR(255) NOT NULL,
  snapshot LONGBLOB NOT NULL,
  meta_ser_id INT,
  meta_ser_manifest VARCHAR(255),
  meta_payload LONGBLOB
) ENGINE=InnoDB;
