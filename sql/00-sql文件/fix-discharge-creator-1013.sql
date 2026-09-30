-- 排放流水登记人：e2e 首条落的是登录用户 id(80370)，修正为昵称（后续由代码写入昵称）
UPDATE mes_set_pollution_discharge SET creator='刘洋' WHERE id=1 AND creator='80370';
