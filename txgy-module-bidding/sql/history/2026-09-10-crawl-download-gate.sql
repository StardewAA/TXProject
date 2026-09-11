-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-10 下载限速闸（bidding_crawl_download_gate）
-- 规范依据：《外部信息获取 v1.0》§六（消费端执行）+ §11（增量回溯/频控）；用户口径 2026-09-10：
--   「下载器的时间间隔必须且只能作用于『下载公告原文文件』这一动作」「每次下载原文文件前必须强制休眠等待」
--   「排队中的任务绝对不允许直接触发下载」。
-- 业务目标：把"原文文件下载"的间隔从**进程内易失变量**升级为**库内原子占位**，使：
--   ① 进程重启后不会立刻发起下载（原实现 lastConsumeAtMs=0 → 重启即下载）；
--   ② 多实例部署下全局仍为"每 interval 一次下载"（原实现各实例各算一份 → 速率 ×N）；
--   ③ 间隔只在下真实下载那一刻消耗（纯本地处理任务不占位）。
-- 为什么用「条件 UPDATE 占位」而不是应用层锁：条件 UPDATE 由 InnoDB 行锁串行化，
--   并发下只有一行能命中（affected=1），天然幂等且跨实例有效，无需引入分布式锁组件。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE（本文件可重复执行，无 DROP/TRUNCATE）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS bidding_crawl_download_gate (
  id               bigint       NOT NULL COMMENT '固定主键（GLOBAL 闸位=1；不用自增，便于幂等初始化）',
  scope_key        varchar(64)  NOT NULL COMMENT '闸位作用域（GLOBAL=全局单闸；保留按源扩展能力）',
  last_download_at  datetime    DEFAULT NULL COMMENT '上次成功占位（=发起原文下载）时刻；NULL=从未下载',
  last_task_id     bigint       DEFAULT NULL COMMENT '上次占位对应的采集任务 id（审计用）',
  last_url         varchar(1024) DEFAULT NULL COMMENT '上次下载的原文地址（审计用）',
  creator          varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater          varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted          bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gate_scope (scope_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原文下载限速闸（原子占位；合规频控唯一真相）';

-- 初始化全局闸位（从未下载：last_download_at=NULL，首次下载不需等待，之后按间隔限速）
INSERT IGNORE INTO bidding_crawl_download_gate (id, scope_key, last_download_at, creator, create_time, updater, update_time, deleted)
VALUES (1, 'GLOBAL', NULL, 'system', NOW(), 'system', NOW(), b'0');

SELECT id, scope_key, last_download_at FROM bidding_crawl_download_gate;
