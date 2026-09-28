-- 标书智能体：清理「运行态幽灵行」（2026-09-21 线上问题修复）
-- 背景：历史版本删除运行中任务时只做逻辑删除（deleted=1），未先落 CANCELED 终态。
-- 逻辑删除后 PENDING/RUNNING 行仍在 running_key 生成列上占位（生成列只看 status），
-- 导致同租户+项目+标段的一键生成被唯一索引 uk_running 永久拦截；
-- 而分页/详情查询自动过滤 deleted=1，用户既看不到也取消不了这条任务。
-- 修复：把已逻辑删除的运行态任务统一转为 CANCELED（running_key 置 NULL、释放槽位）；
-- 代码侧 BidAgentTaskServiceImpl#delete 已同步修正（先落取消终态再逻辑删除）。
UPDATE `bidding_bid_agent_task`
SET `status` = 'CANCELED',
    `finished_at` = NOW()
WHERE `status` IN ('PENDING', 'RUNNING')
  AND `deleted` = 1;
