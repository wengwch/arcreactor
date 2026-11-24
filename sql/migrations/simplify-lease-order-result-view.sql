-- Run once for an existing lease_order_result_view with full order result columns.
-- Apply before starting the projection that writes only the observed refunded amount.
-- Preserve lease/order IDs, refunded_amount, journals and projection offsets.
-- New installations use sql/rentals.sql.
ALTER TABLE lease_order_result_view
  DROP COLUMN intent_id,
  DROP COLUMN amount,
  DROP COLUMN lifecycle,
  DROP COLUMN payment_status,
  DROP COLUMN pending_refund_amount;
