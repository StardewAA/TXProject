-- SAFE ROLLBACK: retain the additive nullable column and all payment-attempt evidence.
-- Stop payment submit/cancel entry points before rolling back the application image: old images
-- do not understand PREPARED/UNKNOWN and can reintroduce the submit-versus-cancel race.
-- This script intentionally does not DROP COLUMN, reset states, delete attempts or enable channels.
-- Explicit, reviewed reconciliation is required for legacy NULL / PREPARED / UNKNOWN attempts.
SELECT 'submit_state retained; payment evidence unchanged; application rollback requires entry-point isolation' AS rollback_note;
