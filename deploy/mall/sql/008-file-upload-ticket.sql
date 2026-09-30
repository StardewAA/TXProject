USE `ruoyi-vue-pro`;

-- ============================================================================
-- 008 文件上传凭证（2026-09-24）
--
-- 背景（③ 复评问题 1）：
--   前端直传（模式二）是「presigned-url 生成 path → 前端 PUT 到对象存储 → /infra/file/create 登记」。
--   原实现里 /infra/file/create 直接采信客户端传入的 configId + path，并把**当前登录用户**记为上传者；
--   而读取又取「最新一条记录」，于是：知道他人文件 path 的任意登录用户，只要调用一次
--   /infra/file/create 重新登记，就能让自己的记录遮蔽原记录，从而取得读取权限（冒领）。
--
-- 修复口径：
--   1. `presigned-url` 由**服务端**为当前登录身份签发一张短时上传凭证（本表）；
--   2. `/infra/file/create` 只有持有本人对应凭证时才允许登记，且**同一 (configId, path)
--      已归属他人时一律拒绝**（不可冒领、不可遮蔽）；
--   3. 同一上传者重复登记按幂等处理（更新元数据，不新增记录）。
--
-- 说明：本表为全局表（不做 DB 层自动租户过滤），租户归属由 tenant_id 显式表达，
--       与 infra_file 一致，故实体上标注 @TenantIgnore。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `infra_file_upload_ticket`
(
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `config_id`       bigint       NOT NULL COMMENT '文件配置编号',
    `path`            varchar(512) NOT NULL COMMENT '服务端生成的文件路径',
    `tenant_id`       bigint       NULL COMMENT '签发时登录身份所属租户编号',
    `owner_user_type` tinyint      NULL COMMENT '签发时用户类型：1=会员 2=后台用户',
    `owner_user_id`   bigint       NULL COMMENT '签发时用户编号',
    `expire_time`     datetime     NOT NULL COMMENT '凭证过期时间',
    `creator`         varchar(64)  NULL DEFAULT '',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`         varchar(64)  NULL DEFAULT '',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         bit(1)       NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_ticket_key` (`config_id`, `path`(191), `owner_user_type`, `owner_user_id`),
    KEY `idx_ticket_expire` (`expire_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='文件上传凭证：预签名下发、登记消费，防止按已知路径冒领文件';
