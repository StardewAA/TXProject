-- 租户 2005：WMS 出库审批按企业层级授权。
-- 第一层：仓库管理员 + 企业超级管理员。
-- 第二层：企业超级管理员。

START TRANSACTION;

-- 宋凯无需额外挂“决策审批人”，由企业超级管理员身份获得两层审批权。
UPDATE `system_user_role`
SET `deleted` = b'1', `updater` = '1', `update_time` = NOW()
WHERE `user_id` = 80168
  AND `role_id` = 60085
  AND `tenant_id` = 2005
  AND `deleted` = b'0';

-- 创建两个可在角色管理中分配的审批权限。
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`,
     `create_time`, `updater`, `update_time`, `deleted`)
SELECT '出库单仓库主管审批', 'wms:shipment-order:approve-supervisor', 3, 9, parent.id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM `system_menu` parent
WHERE parent.id = (
    SELECT source.parent_id FROM `system_menu` source
    WHERE source.permission = 'wms:shipment-order:query' AND source.deleted = b'0' LIMIT 1
)
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE permission = 'wms:shipment-order:approve-supervisor' AND deleted = b'0'
  )
LIMIT 1;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`,
     `create_time`, `updater`, `update_time`, `deleted`)
SELECT '出库单上层决策审批', 'wms:shipment-order:approve-decision', 3, 10, parent.id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM `system_menu` parent
WHERE parent.id = (
    SELECT source.parent_id FROM `system_menu` source
    WHERE source.permission = 'wms:shipment-order:query' AND source.deleted = b'0' LIMIT 1
)
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE permission = 'wms:shipment-order:approve-decision' AND deleted = b'0'
  )
LIMIT 1;

-- 将三个审批权限加入金岩公司当前租户套餐，否则角色权限树会过滤掉它们。
UPDATE `system_tenant_package` package
JOIN `system_tenant` tenant ON tenant.package_id = package.id
JOIN `system_menu` menu ON menu.permission = 'wms:shipment-order:submit-approval' AND menu.deleted = b'0'
SET package.menu_ids = JSON_ARRAY_APPEND(package.menu_ids, '$', menu.id),
    package.updater = '1', package.update_time = NOW()
WHERE tenant.id = 2005
  AND JSON_CONTAINS(package.menu_ids, CAST(menu.id AS JSON)) = 0;

UPDATE `system_tenant_package` package
JOIN `system_tenant` tenant ON tenant.package_id = package.id
JOIN `system_menu` menu ON menu.permission = 'wms:shipment-order:approve-supervisor' AND menu.deleted = b'0'
SET package.menu_ids = JSON_ARRAY_APPEND(package.menu_ids, '$', menu.id),
    package.updater = '1', package.update_time = NOW()
WHERE tenant.id = 2005
  AND JSON_CONTAINS(package.menu_ids, CAST(menu.id AS JSON)) = 0;

UPDATE `system_tenant_package` package
JOIN `system_tenant` tenant ON tenant.package_id = package.id
JOIN `system_menu` menu ON menu.permission = 'wms:shipment-order:approve-decision' AND menu.deleted = b'0'
SET package.menu_ids = JSON_ARRAY_APPEND(package.menu_ids, '$', menu.id),
    package.updater = '1', package.update_time = NOW()
WHERE tenant.id = 2005
  AND JSON_CONTAINS(package.menu_ids, CAST(menu.id AS JSON)) = 0;

-- 仓库管理员：可提交、可执行第一层审批。
-- 企业超级管理员：可提交、可执行第一层和第二层审批。
INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT grants.role_id, menu.id, '1', NOW(), '1', NOW(), b'0', 2005
FROM (
    SELECT 60096 AS role_id, 'wms:shipment-order:submit-approval' AS permission
    UNION ALL SELECT 60096, 'wms:shipment-order:approve-supervisor'
    UNION ALL SELECT 60084, 'wms:shipment-order:submit-approval'
    UNION ALL SELECT 60084, 'wms:shipment-order:approve-supervisor'
    UNION ALL SELECT 60084, 'wms:shipment-order:approve-decision'
) grants
JOIN `system_menu` menu ON menu.permission = grants.permission AND menu.deleted = b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id = grants.role_id
      AND existing.menu_id = menu.id
      AND existing.tenant_id = 2005
      AND existing.deleted = b'0'
);

-- 决策审批人角色不再作为本流程的企业级决策身份。
UPDATE `system_role_menu` role_menu
JOIN `system_menu` menu ON menu.id = role_menu.menu_id
SET role_menu.deleted = b'1', role_menu.updater = '1', role_menu.update_time = NOW()
WHERE role_menu.role_id = 60085
  AND role_menu.tenant_id = 2005
  AND menu.permission IN (
      'wms:shipment-order:submit-approval',
      'wms:shipment-order:approve-supervisor',
      'wms:shipment-order:approve-decision'
  )
  AND role_menu.deleted = b'0';

-- 更新可视化模型：第一层 60096,60084；第二层 60084。
UPDATE `ACT_GE_BYTEARRAY` resource
JOIN `ACT_RE_MODEL` model ON model.EDITOR_SOURCE_VALUE_ID_ = resource.ID_
SET resource.BYTES_ = CONVERT(
    REPLACE(
        REPLACE(
            CONVERT(resource.BYTES_ USING utf8mb4),
            '<flowable:candidateParam><![CDATA[60096,60085,60084]]></flowable:candidateParam>',
            '<flowable:candidateParam><![CDATA[60096,60084]]></flowable:candidateParam>'
        ),
        '<flowable:candidateParam><![CDATA[60085,60084]]></flowable:candidateParam>',
        '<flowable:candidateParam><![CDATA[60084]]></flowable:candidateParam>'
    ) USING binary
)
WHERE model.KEY_ = 'wms_shipment_approval' AND model.TENANT_ID_ = '2005';

UPDATE `ACT_GE_BYTEARRAY` resource
JOIN `ACT_RE_MODEL` model ON model.EDITOR_SOURCE_EXTRA_VALUE_ID_ = resource.ID_
SET resource.BYTES_ = CONVERT(
    JSON_SET(
        CONVERT(resource.BYTES_ USING utf8mb4),
        '$.childNode.candidateParam', '60096,60084',
        '$.childNode.approveMethod', 3,
        '$.childNode.assignStartUserHandlerType', 1,
        '$.childNode.showText', '指定角色：仓库管理员、企业超级管理员',
        '$.childNode.childNode.candidateParam', '60084',
        '$.childNode.childNode.approveMethod', 3,
        '$.childNode.childNode.assignStartUserHandlerType', 1,
        '$.childNode.childNode.showText', '指定角色：企业超级管理员'
    ) USING binary
)
WHERE model.KEY_ = 'wms_shipment_approval' AND model.TENANT_ID_ = '2005';

-- 同步当前已发布版本。模型草稿与流程定义是两份资源，只改草稿不会影响新发起的流程。
UPDATE `ACT_GE_BYTEARRAY` resource
JOIN `ACT_RE_PROCDEF` definition ON definition.DEPLOYMENT_ID_ = resource.DEPLOYMENT_ID_
JOIN (
    SELECT KEY_, TENANT_ID_, MAX(VERSION_) AS VERSION_
    FROM `ACT_RE_PROCDEF`
    WHERE KEY_ = 'wms_shipment_approval' AND TENANT_ID_ = '2005'
    GROUP BY KEY_, TENANT_ID_
) latest ON latest.KEY_ = definition.KEY_
    AND latest.TENANT_ID_ = definition.TENANT_ID_
    AND latest.VERSION_ = definition.VERSION_
SET resource.BYTES_ = CONVERT(
    REPLACE(
        REPLACE(
            CONVERT(resource.BYTES_ USING utf8mb4),
            '<flowable:candidateParam xmlns:flowable="http://flowable.org/bpmn"><![CDATA[60096]]></flowable:candidateParam>',
            '<flowable:candidateParam xmlns:flowable="http://flowable.org/bpmn"><![CDATA[60096,60084]]></flowable:candidateParam>'
        ),
        '<flowable:candidateParam xmlns:flowable="http://flowable.org/bpmn"><![CDATA[60085]]></flowable:candidateParam>',
        '<flowable:candidateParam xmlns:flowable="http://flowable.org/bpmn"><![CDATA[60084]]></flowable:candidateParam>'
    ) USING binary
)
WHERE resource.NAME_ = 'wms_shipment_approval.bpmn';

COMMIT;
