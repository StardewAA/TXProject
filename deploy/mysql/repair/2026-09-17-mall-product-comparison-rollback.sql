-- Stop comparison writes and back up first. Prefer retaining this additive table when reverting code.
-- Never use --force: rollback is allowed only before ANY user selection has been persisted.
DELIMITER $$
DROP PROCEDURE IF EXISTS mall_product_comparison_rollback_guard$$
CREATE PROCEDURE mall_product_comparison_rollback_guard()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema=DATABASE() AND table_name='product_comparison_list') THEN
        SET @comparison_rows = 0;
        SET @comparison_query = 'SELECT COUNT(*) INTO @comparison_rows FROM product_comparison_list';
        PREPARE comparison_check FROM @comparison_query;
        EXECUTE comparison_check;
        DEALLOCATE PREPARE comparison_check;
        IF @comparison_rows > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Comparison selections exist: retain table and fix forward';
        END IF;
        DROP TABLE product_comparison_list;
    END IF;
END$$
CALL mall_product_comparison_rollback_guard()$$
DROP PROCEDURE mall_product_comparison_rollback_guard$$
DELIMITER ;
