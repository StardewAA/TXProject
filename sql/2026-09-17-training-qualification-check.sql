-- ============================================================
-- 上岗资格核查改造（3-5）+ 题库管理改名
-- 日期：2026-09-17
-- 1) 新表 training_qualification_record：核查历史记录（支持删除/批量删除）
-- 2) 菜单改名：考试与认证「题库与审核」→「题库管理」（页面复用资源中心考试题库）
-- ============================================================

-- 1. 核查历史记录表
CREATE TABLE IF NOT EXISTS training_qualification_record (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录 ID',
    user_id      BIGINT       NULL COMMENT '平台用户 ID（人员不存在时为空）',
    user_name    VARCHAR(64)  NULL COMMENT '核查输入/匹配到的姓名',
    mobile       VARCHAR(20)  NULL COMMENT '核查输入的手机号',
    qualified    BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否具备上岗资格',
    passed_posts VARCHAR(512) NULL COMMENT '通过岗位（JSON 数组，如 ["管道工","防腐工"]）',
    reason       VARCHAR(500) NULL COMMENT '结论说明',
    detail_json  TEXT         NULL COMMENT '核查明细（培训班列表 JSON）',
    creator      VARCHAR(64)           DEFAULT '' COMMENT '创建者',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater      VARCHAR(64)           DEFAULT '' COMMENT '更新者',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (id),
    KEY idx_tqr_mobile (mobile),
    KEY idx_tqr_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '上岗资格核查历史记录';

-- 2. 菜单改名：题库与审核 → 题库管理
UPDATE system_menu SET name = '题库管理' WHERE id = 41701;
