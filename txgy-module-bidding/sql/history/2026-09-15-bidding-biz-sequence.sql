# ----------------------------------------------------------------------
# 内部业务编号序列（原子递增，防并发重复） —— 2026-09-15
# 依据：《电子招标投标系统技术规范 第1部分 交易平台》附录B.3.2 项目编号规则：
#   编号 17 位，序列号段采用 0-9、a-z 的 6 位组合序列号（原 000001-999999 扩展）。
# 本表承载序列号原子递增（INSERT ... ON DUPLICATE KEY UPDATE + LAST_INSERT_ID），
# 避免应用侧读-改-写在多实例并发下重号。
# G 轨全局序列：不含 tenant_id（内部编号全局唯一，不能按租户分桶撞号）；
# 无 deleted（序列行不做逻辑删除，只增不改）；主键 = (biz_type, scope_key) 天然一桶一行。
# -- 破坏性约束：仅 CREATE TABLE IF NOT EXISTS，无 DROP/TRUNCATE/DELETE，可重复执行。
CREATE TABLE IF NOT EXISTS `bidding_biz_sequence` (
    `biz_type` varchar(16) NOT NULL COMMENT '业务类型：NOTICE=公告 / FILING=备案回执 / PROJECT=内部项目',
    `scope_key` varchar(32) NOT NULL COMMENT '分桶键：年度_区划（如 2026_610000），按桶各段独立连续',
    `seq` bigint NOT NULL DEFAULT 1 COMMENT '当前序号（1 起；由 UPSERT 维护）',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '首次取号时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近取号时间',
    PRIMARY KEY (`biz_type`, `scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部业务编号序列（原子递增，G轨）';
