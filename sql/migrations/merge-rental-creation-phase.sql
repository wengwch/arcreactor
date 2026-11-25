-- Run after stopping the old rental service and before enabling queries with the merged phase.
-- Preserve the saved amount, workflow_version, journal and projection offset.
UPDATE lease_view
SET pending_phase = 'CREATING_ORDER'
WHERE pending_phase = 'PRICING';
