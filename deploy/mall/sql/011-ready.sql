USE `ruoyi-vue-pro`;

-- Last initialization step. Do not start applications on a partial ACL schema.
SELECT `access_type`, `bind_status`, `owner_user_type`, `owner_user_id`,
       `promote_status`, `promote_fail_count`, `promote_last_error`
FROM `infra_file` LIMIT 0;
SELECT 1 FROM `infra_file_upload_ticket` LIMIT 0;
SELECT 1 FROM `member_platform_account` LIMIT 0;
SELECT 1 FROM `member_platform_account_link` LIMIT 0;
SELECT 1 FROM `member_platform_tenant_membership` LIMIT 0;
SELECT 1 FROM `product_enterprise` LIMIT 0;
SELECT 1 FROM `trade_erp_inventory_request` LIMIT 0;

INSERT INTO `mall_release` (`version`) VALUES ('2026-09-25-file-acl');
