-- Rebuild only the administrator workbench projection cursor.
-- Business orders, after-sales records, reminder events and read states are preserved.
-- The next administrator summary request rescans active facts and adds reminders for
-- after-sale applications that existed before AFTER_AUDIT became an administrator action.
DELETE FROM trade_order_workbench_scope
WHERE scope_kind = 'ADMIN'
  AND scope_id = 0;
