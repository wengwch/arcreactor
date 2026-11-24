-- Run once on existing rental tables before using the mappers without result_version.
-- The old lease_order_result_view column is NOT NULL with no default and would reject inserts.
-- New installations use sql/rentals.sql directly.
ALTER TABLE lease_order_result_view DROP COLUMN result_version;
ALTER TABLE order_view DROP COLUMN result_version;
