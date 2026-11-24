-- Run once only for an order_view created before actor polling replaced notifications and scans.
-- Apply after stopping the old application; preserve workflow_version and projection offsets.
-- Tables created with the current sql/rentals.sql do not need this migration.
ALTER TABLE order_view
  DROP INDEX idx_order_due,
  DROP COLUMN acknowledged_version,
  DROP COLUMN next_action_at;
