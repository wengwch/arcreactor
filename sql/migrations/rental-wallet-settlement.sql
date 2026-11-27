-- Schema upgrade for unused rental tables from the previous mock-payment implementation.
-- This is not a migration of old payment/refund events or live leases into wallet freezes.
-- Do not apply to active mock leases: their orders have no corresponding wallet lock.
-- Historical detail tables are retained; the wallet model no longer reads or writes them.
-- New installations use sql/rentals.sql.
ALTER TABLE lease_view
  ADD COLUMN current_intent_id VARCHAR(255) AFTER current_order_id,
  CHANGE COLUMN current_paid_amount current_amount BIGINT,
  CHANGE COLUMN paid_through last_period_ends_at TIMESTAMP(6) NULL,
  DROP COLUMN pending_valid,
  ADD COLUMN settlement_order_id VARCHAR(255),
  ADD COLUMN settlement_intent_id VARCHAR(255) AFTER settlement_order_id,
  ADD COLUMN settlement_amount BIGINT AFTER settlement_intent_id,
  ADD COLUMN settlement_deduct_amount BIGINT AFTER settlement_amount;

ALTER TABLE order_view
  ADD COLUMN user_id VARCHAR(255) AFTER order_id,
  DROP COLUMN lifecycle,
  DROP COLUMN payment_status,
  ADD COLUMN phase VARCHAR(64) NOT NULL DEFAULT 'NEW' AFTER amount,
  ADD COLUMN deduct_amount BIGINT AFTER phase;
