-- Run once against the existing user_region_share table before starting the updated application.
-- Existing regional shares become SYSTEM; no resource, credential or projection offset is removed.
ALTER TABLE user_region_share
  ADD COLUMN share_kind VARCHAR(16) NOT NULL DEFAULT 'SYSTEM'
    COMMENT 'SYSTEM or USER; not the Manila share type' AFTER share_id,
  ADD COLUMN system_region_id VARCHAR(255) GENERATED ALWAYS AS
    (CASE WHEN share_kind = 'SYSTEM' THEN region_id ELSE NULL END) STORED AFTER share_kind,
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (user_id, share_id),
  ADD UNIQUE KEY uk_user_system_share_region (user_id, system_region_id),
  ADD INDEX idx_user_share_region_kind (user_id, region_id, share_kind);
