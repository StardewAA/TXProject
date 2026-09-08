-- job 加 produced 列：修复投递-消费竞态（生产端投递完置 1；tryFinalizeJob 要求 produced=1 且无未终态 task 才收尾）
ALTER TABLE bidding_fetch_job
    ADD COLUMN produced bit(1) NOT NULL DEFAULT b'0'
        COMMENT '生产端是否已投递完成（async：runNow 投递完置1；消费端收尾条件=produced且无未终态task，防提前收尾）' AFTER status;
