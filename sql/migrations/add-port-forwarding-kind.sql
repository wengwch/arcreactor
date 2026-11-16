-- Run once for an existing port_forwarding table; new installations use sql/read-model.sql.
-- Deletions of unknown claims have no creation specification and leave kind NULL.
ALTER TABLE port_forwarding
  ADD COLUMN kind VARCHAR(16) AFTER os_project_id;
