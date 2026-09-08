-- ----------------------------------------------------------------------------
-- 修复：mock 渠道 config 缺少 @class 多态属性导致"系统异常"
-- PayClientConfig 接口使用 @JsonTypeInfo(Id.CLASS)，config 必须携带 @class
-- 范围：config = '{}' 的渠道记录
-- 幂等：仅更新 '{}'，可重复执行
-- ----------------------------------------------------------------------------
UPDATE `pay_channel`
SET `config` = '{"@class":"cn.iocoder.txgy.module.pay.framework.pay.core.client.impl.NonePayClientConfig","name":"none-config"}'
WHERE `code` = 'mock' AND `config` = '{}';

-- 验证
SELECT id, app_id, code, status, config FROM `pay_channel` WHERE `code` = 'mock' AND `config` = '{}';
SELECT COUNT(*) AS fixed_total FROM `pay_channel` WHERE `code` = 'mock';
