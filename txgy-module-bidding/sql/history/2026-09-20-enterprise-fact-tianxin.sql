-- 延安天信管业集团 企业事实录入（COPY_FACT 通道数据源）
-- 用途：让标书模板回填（fill 阶段）能从企业资料取到投标人/资质/报价等字段值
-- 说明：name 用标准名称 + 常见别名，配合 normalizeName 模糊匹配模板 SDT 字段名
-- 幂等：先清空该租户该来源的旧数据再插入（避免重复录入同一份资料）
SET NAMES utf8mb4;

DELETE FROM `bidding_bid_enterprise_fact`
WHERE tenant_id = 1 AND source_doc_name = '延安天信管业集团_企业资料.md';

INSERT INTO `bidding_bid_enterprise_fact`
    (`type`, `name`, `value`, `unit`, `valid_until`, `source_doc_name`, `source_block_ids`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    -- 一、投标人基本信息（LICENSE）
    ('LICENSE', '投标人', '延安天信管业集团有限公司', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '投标人名称', '延安天信管业集团有限公司', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '投标人全称', '延安天信管业集团有限公司', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '企业简称', '天信管业', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '法定代表人', '张建国', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '统一社会信用代码', '91610600MA6YH3T8X2', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '注册资本', '壹亿贰仟万元整', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '成立日期', '2013年05月18日', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '成立时间', '2013年05月18日', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '注册地址', '陕西省延安市高新技术产业开发区工业大道 12 号', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '地址', '陕西省延安市高新技术产业开发区工业大道 12 号', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '单位性质', '有限责任公司（自然人投资或控股）', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '企业类型', '有限责任公司（自然人投资或控股）', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '经营范围', '塑料管材管件、球墨铸铁管、防腐钢管的研发、生产、销售、安装；市政公用工程施工；水利水电工程施工；管道工程施工；货物及技术进出口', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '联系电话', '0911-8866777', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '联系人', '李梅（市场部经理）', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('LICENSE', '参保人数', '486 人', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    -- 二、企业资质（QUAL）
    ('QUAL', '防水防腐保温工程专业承包', '贰级', NULL, '2027-09-12', '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('QUAL', '市政公用工程施工总承包', '贰级', NULL, '2028-06-30', '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('QUAL', '水利水电工程施工总承包', '叁级', NULL, '2027-11-15', '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('QUAL', '管道工程专业承包', '贰级', NULL, '2028-03-20', '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    -- 五、报价口径（PRICE）
    ('PRICE', '增值税税率', '13%', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('PRICE', '预付款', '20%', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('PRICE', '质保金', '3%', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('PRICE', '质保期', '管材本体5年、安装工程2年', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('PRICE', '质量保证期', '管材本体5年、安装工程2年', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('PRICE', '报价方式', '固定综合单价法', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    -- 四、类似业绩（PERFORMANCE）
    ('PERFORMANCE', '类似业绩', '近三年类似业绩10项，累计合同额约19644.50万元', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0'),
    ('PERFORMANCE', '业绩', '近三年类似业绩10项，累计合同额约19644.50万元', NULL, NULL, '延安天信管业集团_企业资料.md', NULL, 1, '1', NOW(), '1', NOW(), b'0');
