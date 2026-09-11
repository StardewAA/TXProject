-- 商城营销简化模式（可重复执行）
--
-- 防腐保温商城以企业采购为主，当前只在后台展示并使用：
--   采购优惠（限时折扣）、采购满额优惠、优惠券、内容公告和客服。
-- 秒杀、砍价、拼团、积分商城、分销和复杂装修暂不作为日常业务入口。
-- 本脚本只隐藏菜单，不删除表、接口或历史数据，后续需要恢复时将 visible 改回 1 即可。

UPDATE system_menu
SET visible = b'0', updater = 'marketing-simple-mode', update_time = NOW()
WHERE deleted = b'0'
  AND (
      path IN (
          'seckill', 'bargain', 'combination', 'point-activity',
          'diy-template', 'diy-page', 'brokerage',
          'brokerage-user', 'brokerage-record', 'brokerage-withdraw'
      )
      OR component LIKE 'mall/promotion/seckill/%'
      OR component LIKE 'mall/promotion/bargain/%'
      OR component LIKE 'mall/promotion/combination/%'
      OR component LIKE 'mall/promotion/point/%'
      OR component LIKE 'mall/promotion/diy/%'
      OR component LIKE 'mall/trade/brokerage/%'
      -- 兼容历史库中带业务后缀的子权限（如 seckill-activity、seckill-config）。
      -- 保留旧前缀条件，避免不同版本初始化脚本漏隐藏权限按钮。
      OR permission LIKE 'promotion:seckill:%'
      OR permission LIKE 'promotion:seckill-activity:%'
      OR permission LIKE 'promotion:seckill-config:%'
      OR permission LIKE 'promotion:bargain:%'
      OR permission LIKE 'promotion:bargain-activity:%'
      OR permission LIKE 'promotion:bargain-record:%'
      OR permission LIKE 'promotion:bargain-help:%'
      OR permission LIKE 'promotion:combination:%'
      OR permission LIKE 'promotion:combination-activity:%'
      OR permission LIKE 'promotion:combination-record:%'
      OR permission LIKE 'promotion:point-activity:%'
      OR permission LIKE 'promotion:diy:%'
      OR permission LIKE 'promotion:diy-template:%'
      OR permission LIKE 'promotion:diy-page:%'
      OR permission LIKE 'trade:brokerage-%'
      OR name IN (
          '秒杀活动', '秒杀商品', '秒杀时段',
          '砍价活动', '砍价商品', '砍价记录',
          '拼团活动', '拼团商品', '拼团记录',
          '积分商城', '商城装修', '装修模板', '装修页面',
          '分销管理', '分销用户', '佣金记录', '佣金提现'
      )
  );

-- 保留的入口改成采购语义，避免后台继续显示消费电商术语。
UPDATE system_menu
SET name = '采购满额优惠', updater = 'marketing-simple-mode', update_time = NOW()
WHERE deleted = b'0'
  AND path = 'reward-activity'
  AND (name = '满减送' OR name = '满减');

UPDATE system_menu
SET name = '采购优惠', updater = 'marketing-simple-mode', update_time = NOW()
WHERE deleted = b'0'
  AND path = 'discount-activity'
  AND (name = '限时折扣' OR name = '折扣活动');

UPDATE system_menu
SET name = '采购优惠券', updater = 'marketing-simple-mode', update_time = NOW()
WHERE deleted = b'0'
  AND path = 'coupon'
  AND (name = '优惠劵' OR name = '优惠券');
