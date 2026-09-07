-- 清理历史污染判定演示数据：1-10 为指向虚构单号(PO/MO/WO/FG…)的旧演示行，
-- 11-14 为租户2010误测试行(biz 12321/asdas/NULL)，全部删除，保留 id=15 真实单据行。
DELETE FROM mes_set_pollution_check WHERE id BETWEEN 1 AND 14;
