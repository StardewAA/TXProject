-- Remove bidding seed rows that were imported with a mangled encoding, so the
-- module's idempotent initial-state package can reinsert them byte-accurately.
SET NAMES utf8mb4;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 6735 AND 6776;
DELETE FROM system_menu      WHERE id      BETWEEN 6735 AND 6776;
DELETE FROM bidding_fetch_source       WHERE id IN (100000001, 690010001, 690010002);
DELETE FROM bidding_crawl_download_gate WHERE id = 1;
