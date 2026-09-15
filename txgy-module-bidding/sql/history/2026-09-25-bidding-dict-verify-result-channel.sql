-- =====================================================================
-- 验真结论字典 + 验真渠道补齐（承接 2026-09-24-bidding-dict-key-post.sql）
-- =====================================================================
-- 背景：本轮接线后端新加的"验真历史分页"读出口（GET /bidding/certificate-verify/history/page）。
--       该接口的 Javadoc 把病根写得很清楚："/verify 与 /verify-file 每次都返回验真历史主键，但此前没有任何接口
--       能把它读回来——留痕只进不出，主键等于一个再也打不开的句柄"。读出口补上了，前端要按 code 展示，
--       于是暴露两处字典缺口：
--         (1) verify_result 整个字典不存在（页面只能直出 MATCH/MISMATCH/UNAVAILABLE）；
--         (2) verify_channel 只有 3 行，缺 THIRD_PARTY —— 经查证枚举 CertVerifyChannelEnum 有 4 个值
--             （规范出处：设计文档 v3.3 §17.5"四库一平台/部级/省级/第三方"），属注册时漏行；
--             若真有证照走第三方通道核验，界面会直出英文 code。
-- 取值来源（不臆造）：
--   verify_result  ← CertVerifyResultEnum（MATCH/MISMATCH/UNAVAILABLE），
--                    口径见其 Javadoc：GB/T 36906—2018 §6.4 verifyinfo result 0=校验正确→MATCH；
--                    1/2/3（未激活/暂时失效/已失效）→MISMATCH；4（信息不全）/5（查无此信息）→UNAVAILABLE。
--   verify_channel ← CertVerifyChannelEnum.THIRD_PARTY（第三方核验通道）。
-- 注意：verify_result 与已有的 verification_status **不是一回事**——
--       verification_status 是"证照的验真状态"（UNVERIFIED/VERIFYING/VERIFIED/INVALID，一个证照一个当前态），
--       verify_result 是"每一次验真尝试的结论"（历史表逐行），二者语义不同，不能合并成一套编码。
-- 设计说明：id 沿用 100 段分块：verify_result 占 44xx（4401-4403），verify_channel 补行占 45 01，不复用旧 id；幂等 INSERT IGNORE。
-- 执行库：ruoyi-vue-pro。
-- 校验：SELECT dict_type, dict_value, dict_label FROM bidding_dict_data
--       WHERE dict_type IN ('verify_result','verify_channel') ORDER BY dict_type, sort;  -- 期望 3 + 4 行
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- verify_result（新增类型 3 条）
(710000000000004401,0,'verify_result','MATCH','与官方底账一致','success',b'1',1,b'1','GB/T 36906 verifyinfo result=0（校验正确）','1',NOW(),'1',NOW(),b'0'),
(710000000000004402,0,'verify_result','MISMATCH','与官方底账不一致','danger',b'1',2,b'1','result=1/2/3（未激活/暂时失效/已失效）→ 状态异常','1',NOW(),'1',NOW(),b'0'),
(710000000000004403,0,'verify_result','UNAVAILABLE','无法判定','warning',b'0',3,b'1','result=4/5（信息不全/查无此信息）→ 不可得，可重试','1',NOW(),'1',NOW(),b'0'),
-- verify_channel 补齐（第 4 个渠道）
(710000000000004501,0,'verify_channel','THIRD_PARTY','第三方核验通道','default',b'0',4,b'1','设计文档 v3.3 §17.5 四渠道之一（本轮按 CertVerifyChannelEnum 补齐漏行）','1',NOW(),'1',NOW(),b'0');
