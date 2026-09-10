-- 租户 2005：扩展 WMS 出库审批的角色层级。
-- 仓库主管节点：仓库管理员、决策审批人、企业超级管理员。
-- 上级复核节点：决策审批人、企业超级管理员。

START TRANSACTION;

-- 宋凯同时担任企业超级管理员和决策审批人。
INSERT INTO `system_user_role`
    (`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 80168, 60085, '1', NOW(), '1', NOW(), b'0', 2005
WHERE NOT EXISTS (
    SELECT 1
    FROM `system_user_role`
    WHERE user_id = 80168
      AND role_id = 60085
      AND tenant_id = 2005
      AND deleted = b'0'
);

INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT role_ids.role_id, approval_menu.id, '1', NOW(), '1', NOW(), b'0', 2005
FROM (
    SELECT 60096 AS role_id
    UNION ALL SELECT 60085
    UNION ALL SELECT 60084
) role_ids
JOIN `system_menu` approval_menu
  ON approval_menu.permission = 'wms:shipment-order:submit-approval'
 AND approval_menu.deleted = b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` existing
    WHERE existing.role_id = role_ids.role_id
      AND existing.menu_id = approval_menu.id
      AND existing.tenant_id = 2005
      AND existing.deleted = b'0'
);

UPDATE `ACT_GE_BYTEARRAY` resource
JOIN `ACT_RE_MODEL` model ON model.EDITOR_SOURCE_VALUE_ID_ = resource.ID_
SET resource.BYTES_ = CONVERT(
    REPLACE(
        REPLACE(
            CONVERT(resource.BYTES_ USING utf8mb4),
            '<flowable:candidateParam><![CDATA[60096]]></flowable:candidateParam>',
            '<flowable:candidateParam><![CDATA[60096,60085,60084]]></flowable:candidateParam>'
        ),
        '<flowable:candidateParam><![CDATA[60085]]></flowable:candidateParam>',
        '<flowable:candidateParam><![CDATA[60085,60084]]></flowable:candidateParam>'
    ) USING binary
)
WHERE model.KEY_ = 'wms_shipment_approval'
  AND model.TENANT_ID_ = '2005';

UPDATE `ACT_GE_BYTEARRAY` resource
JOIN `ACT_RE_MODEL` model ON model.EDITOR_SOURCE_EXTRA_VALUE_ID_ = resource.ID_
SET resource.BYTES_ = CONVERT(
    JSON_SET(
        CONVERT(resource.BYTES_ USING utf8mb4),
        '$.childNode.candidateParam', '60096,60085,60084',
        '$.childNode.showText', '指定角色：仓库管理员、决策审批人、企业超级管理员',
        '$.childNode.childNode.candidateParam', '60085,60084',
        '$.childNode.childNode.showText', '指定角色：决策审批人、企业超级管理员'
    ) USING binary
)
WHERE model.KEY_ = 'wms_shipment_approval'
  AND model.TENANT_ID_ = '2005';

COMMIT;
