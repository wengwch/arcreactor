-- Run once if port_forwarding was created before the optional domain was introduced.
-- New installations should use sql/read-model.sql directly.
ALTER TABLE port_forwarding
  ADD COLUMN domain VARCHAR(255) NULL AFTER floating_ip;
