SET NAMES utf8mb4;

-- 2026-09-22 retire the admin "bid generation" page.
-- Keep backend/portal generation APIs alive:
--   6804 create and 6805 cancel permissions are re-parented under 6790 "bid-agent".
--   6800 page menu and 6803 duplicate query permission are logically deleted.
UPDATE system_menu
   SET parent_id = 6790, sort = 20, updater = '1', update_time = NOW()
 WHERE id IN (6804, 6805)
   AND deleted = b'0';

UPDATE system_menu
   SET deleted = b'1', status = 1, visible = b'0', updater = '1', update_time = NOW()
 WHERE id IN (6800, 6803)
   AND deleted = b'0';

DELETE FROM system_role_menu WHERE menu_id IN (6800, 6803);

SELECT id, name, type, parent_id, path, component, deleted
  FROM system_menu
 WHERE id IN (6800, 6803, 6804, 6805)
 ORDER BY id;