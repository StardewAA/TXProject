-- ---------------------------------------------------------------------
-- 招标交易代码集 123 类共 626 条下沉业务字典（bidding_dict_data）
-- 整改依据：用户核查结论③：规范照抄枚举数据改为数据库字典，禁止 Java 常量堆积
-- 出处：《VO 数据对象规范》第十章（陕西/国家 各代码表）
-- 幂等：INSERT IGNORE；类型经 MetaDictServiceImpl 按字典表数据自动支持（/bidding/meta/dicts）
-- id 分块：7100000000000079xx 起（避开已用 70xx/73xx/76xx 段）
-- 2026-10 号段修正（check-seed-id-collision.js 报出，本文件为"后出现"一方）：本文件与既有
--   2026-09-20-cert-category-dict.sql（710000000000008101~8121）、2026-09-21-cert-change-log-dict.sql
--   （710000000000008201~8215）撞号共 24 个 ⇒ 两者同时并入一键包时，后执行的 INSERT IGNORE 会**静默整行跳过**，
--   导致 national_reward_punish_type_136 / national_section_classify *_36~38 / shaanxi_abnormal* /
--   shaanxi_amount_unit_* 等字典在库里缺行。按门禁建议把撞号的 24 个 id 整体上移到空闲号段
--   7100000000000018101~18215（该段在 sql/ 与 sql/history/ 均无占用）。
-- dict_type 命名：类名去 Enum 转 snake_case（保留规范章节后缀如 _1/_413/_56）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAlertLevelEnum.java → national_alert_level（3 条）
(710000000000007901,0,'national_alert_level','01','违法','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007902,0,'national_alert_level','02','违规','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007903,0,'national_alert_level','99','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAmountUnit_8Enum.java → national_amount_unit_8（2 条）
(710000000000007904,0,'national_amount_unit_8','0','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007905,0,'national_amount_unit_8','1','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAssetTradeModeEnum.java → national_asset_trade_mode（6 条）
(710000000000007906,0,'national_asset_trade_mode','1','网络竞价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007907,0,'national_asset_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007908,0,'national_asset_trade_mode','3','招投标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007909,0,'national_asset_trade_mode','4','~~协议转让（非竞价）~~','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007910,0,'national_asset_trade_mode','5','~~动态报价~~','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007911,0,'national_asset_trade_mode','99','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAssetTypeEnum.java → national_asset_type（6 条）
(710000000000007912,0,'national_asset_type','1','房产','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007913,0,'national_asset_type','2','土地使用权','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007914,0,'national_asset_type','3','交通运输工具','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007915,0,'national_asset_type','4','设备','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007916,0,'national_asset_type','5','在建工程','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007917,0,'national_asset_type','99','其他资产','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAuctionType_56Enum.java → national_auction_type_56（6 条）
(710000000000007918,0,'national_auction_type_56','0','招标出让','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007919,0,'national_auction_type_56','1','挂牌出让','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007920,0,'national_auction_type_56','2','拍卖出让','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007921,0,'national_auction_type_56','3','网上挂牌出让','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007922,0,'national_auction_type_56','4','网上拍卖出让','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007923,0,'national_auction_type_56','9','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalComplianceStatusEnum.java → national_compliance_status（3 条）
(710000000000007924,0,'national_compliance_status','1','合格','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007925,0,'national_compliance_status','2','不合格','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007926,0,'national_compliance_status','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalDissentTypeEnum.java → national_dissent_type（5 条）
(710000000000007927,0,'national_dissent_type','1','资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007928,0,'national_dissent_type','2','招标文件','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007929,0,'national_dissent_type','3','开标过程','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007930,0,'national_dissent_type','4','资格预审结果','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007931,0,'national_dissent_type','5','评标结果','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalEvaluationLevelEnum.java → national_evaluation_level（4 条）
(710000000000007932,0,'national_evaluation_level','1','优秀','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007933,0,'national_evaluation_level','2','良好','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007934,0,'national_evaluation_level','3','合格','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007935,0,'national_evaluation_level','4','不合格','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalEvaluationModeEnum.java → national_evaluation_mode（4 条）
(710000000000007936,0,'national_evaluation_mode','1','电子','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007937,0,'national_evaluation_mode','2','线下','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007938,0,'national_evaluation_mode','3','远程异地评标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007939,0,'national_evaluation_mode','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalEvaluationResultEnum.java → national_evaluation_result（3 条）
(710000000000007940,0,'national_evaluation_result','1','招标失败','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007941,0,'national_evaluation_result','2','重新招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007942,0,'national_evaluation_result','3','评标结果公示','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalExpertTypeEnum.java → national_expert_type（2 条）
(710000000000007943,0,'national_expert_type','1','在库专家','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007944,0,'national_expert_type','2','招标人代表','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalGenderEnum.java → national_gender（2 条）
(710000000000007945,0,'national_gender','0','男','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007946,0,'national_gender','1','女','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalIdCardTypeEnum.java → national_id_card_type（27 条）
(710000000000007947,0,'national_id_card_type','01','居民身份证','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007948,0,'national_id_card_type','02','军官证','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007949,0,'national_id_card_type','03','武警警官证','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007950,0,'national_id_card_type','04','士兵证','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007951,0,'national_id_card_type','05','军队离退休干部证','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007952,0,'national_id_card_type','06','残疾人证','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007953,0,'national_id_card_type','07','残疾军人证（1-8级）','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007954,0,'national_id_card_type','08','护照','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007955,0,'national_id_card_type','09','港澳同胞回乡证','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007956,0,'national_id_card_type','10','港澳居民来往内地通行证','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007957,0,'national_id_card_type','11','中华人民共和国往来港澳通行证','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007958,0,'national_id_card_type','12','台湾居民来往大陆通行证','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007959,0,'national_id_card_type','13','大陆居民往来台湾通行证','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007960,0,'national_id_card_type','14','外国人居留证','default',b'0',14,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007961,0,'national_id_card_type','15','外交官证','default',b'0',15,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007962,0,'national_id_card_type','16','领事馆证','default',b'0',16,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007963,0,'national_id_card_type','17','海员证','default',b'0',17,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007964,0,'national_id_card_type','18','香港身份证','default',b'0',18,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007965,0,'national_id_card_type','19','台湾身份证','default',b'0',19,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007966,0,'national_id_card_type','20','澳门身份证','default',b'0',20,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007967,0,'national_id_card_type','21','外国人身份证件','default',b'0',21,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007968,0,'national_id_card_type','22','高校毕业生自主创业证','default',b'0',22,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007969,0,'national_id_card_type','23','就业失业登记证','default',b'0',23,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007970,0,'national_id_card_type','24','台胞证','default',b'0',24,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007971,0,'national_id_card_type','25','退休证','default',b'0',25,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007972,0,'national_id_card_type','26','离休证','default',b'0',26,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007973,0,'national_id_card_type','99','其他证件','default',b'0',27,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalIllegalTypeEnum.java → national_illegal_type（3 条）
(710000000000007974,0,'national_illegal_type','1','违法','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007975,0,'national_illegal_type','2','违规','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007976,0,'national_illegal_type','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalLegalPersonCategoryEnum.java → national_legal_person_category（5 条）
(710000000000007977,0,'national_legal_person_category','1','企业','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007978,0,'national_legal_person_category','2','机关法人','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007979,0,'national_legal_person_category','3','事业单位','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007980,0,'national_legal_person_category','4','社会组织','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007981,0,'national_legal_person_category','9','其他','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalMarginPayType_39Enum.java → national_margin_pay_type_39（7 条）
(710000000000007982,0,'national_margin_pay_type_39','1','资金现金','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007983,0,'national_margin_pay_type_39','2','银行保函','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007984,0,'national_margin_pay_type_39','3','担保','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007985,0,'national_margin_pay_type_39','4','电汇','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007986,0,'national_margin_pay_type_39','5','汇票','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007987,0,'national_margin_pay_type_39','6','支票','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007988,0,'national_margin_pay_type_39','9','其他','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalNoticeNatureEnum.java → national_notice_nature（4 条）
(710000000000007989,0,'national_notice_nature','1','正常公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007990,0,'national_notice_nature','2','更正公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007991,0,'national_notice_nature','3','重发公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007992,0,'national_notice_nature','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalNoticeType_33Enum.java → national_notice_type_33（4 条）
(710000000000007993,0,'national_notice_type_33','1','招标公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007994,0,'national_notice_type_33','2','资格预审公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007995,0,'national_notice_type_33','3','中标结果公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007996,0,'national_notice_type_33','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalNoticeType_58Enum.java → national_notice_type_58（3 条）
(710000000000007997,0,'national_notice_type_58','1','正常公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007998,0,'national_notice_type_58','2','补充公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007999,0,'national_notice_type_58','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalOpenResultEnum.java → national_open_result（3 条）
(710000000000008000,0,'national_open_result','1','开标结束','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008001,0,'national_open_result','2','招标失败','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008002,0,'national_open_result','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPreQualifyMode_40Enum.java → national_pre_qualify_mode_40（2 条）
(710000000000008003,0,'national_pre_qualify_mode_40','1','资格预审','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008004,0,'national_pre_qualify_mode_40','2','资格后审','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPriceForm_9Enum.java → national_price_form_9（3 条）
(710000000000008005,0,'national_price_form_9','1','金额','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008006,0,'national_price_form_9','2','费率/比率/优惠率/合格率等','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008007,0,'national_price_form_9','3','其他形式','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalProjectCategoryRelEnum.java → national_project_category_rel（2 条）
(710000000000008008,0,'national_project_category_rel','0','无','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008009,0,'national_project_category_rel','1','固定资产类项目','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPublicityTypeEnum.java → national_publicity_type（3 条）
(710000000000008010,0,'national_publicity_type','1','正常','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008011,0,'national_publicity_type','2','更正','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008012,0,'national_publicity_type','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPurchaseMode_52Enum.java → national_purchase_mode_52（7 条）
(710000000000008013,0,'national_purchase_mode_52','1','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008014,0,'national_purchase_mode_52','2','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008015,0,'national_purchase_mode_52','3','竞争性谈判','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008016,0,'national_purchase_mode_52','4','单一来源采购','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008017,0,'national_purchase_mode_52','5','询价','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008018,0,'national_purchase_mode_52','6','竞争性磋商','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008019,0,'national_purchase_mode_52','9','其他','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPurchaseOrganizeForm_53Enum.java → national_purchase_organize_form_53（3 条）
(710000000000008020,0,'national_purchase_organize_form_53','1','集中采购','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008021,0,'national_purchase_organize_form_53','2','分散采购','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008022,0,'national_purchase_organize_form_53','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalQualificationLevelEnum.java → national_qualification_level（13 条）
(710000000000008023,0,'national_qualification_level','01','特级','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008024,0,'national_qualification_level','02','一级','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008025,0,'national_qualification_level','03','二级','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008026,0,'national_qualification_level','04','三级','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008027,0,'national_qualification_level','05','四级','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008028,0,'national_qualification_level','06','甲级','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008029,0,'national_qualification_level','07','乙级','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008030,0,'national_qualification_level','08','丙级','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008031,0,'national_qualification_level','09','丁级','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008032,0,'national_qualification_level','10','暂定级（预乙级）','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008033,0,'national_qualification_level','11','初级','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008034,0,'national_qualification_level','12','中级','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008035,0,'national_qualification_level','13','高级','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalQualificationTypeEnum.java → national_qualification_type（49 条）
(710000000000008036,0,'national_qualification_type','010001','一级注册建筑师','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008037,0,'national_qualification_type','010002','二级注册建筑师','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008038,0,'national_qualification_type','010101','一级注册结构工程师','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008039,0,'national_qualification_type','010102','二级注册结构工程师','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008040,0,'national_qualification_type','010201','一级注册建造师','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008041,0,'national_qualification_type','010202','二级注册建造师','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008042,0,'national_qualification_type','010203','一级临时注册建造师','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008043,0,'national_qualification_type','010204','二级临时注册建造师','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008044,0,'national_qualification_type','010301','注册土木工程师（岩土）','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008045,0,'national_qualification_type','010302','注册土木工程师（港口与航道工程）','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008046,0,'national_qualification_type','010303','注册土木工程师（水利水电工程）','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008047,0,'national_qualification_type','010304','注册土木工程师（道路工程）','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008048,0,'national_qualification_type','010401','注册公用设备工程师（暖通空调）','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008049,0,'national_qualification_type','010402','注册公用设备工程师（给水排水）','default',b'0',14,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008050,0,'national_qualification_type','010403','注册公用设备工程师（动力）','default',b'0',15,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008051,0,'national_qualification_type','010501','注册电气工程师（发输变电）','default',b'0',16,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008052,0,'national_qualification_type','010502','注册电气工程师（供配电）','default',b'0',17,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008053,0,'national_qualification_type','010601','注册化工工程师','default',b'0',18,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008054,0,'national_qualification_type','010701','注册监理工程师','default',b'0',19,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008055,0,'national_qualification_type','010801','注册造价工程师','default',b'0',20,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008056,0,'national_qualification_type','010802','注册造价师','default',b'0',21,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008057,0,'national_qualification_type','010901','勘察设计注册工程师','default',b'0',22,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008058,0,'national_qualification_type','010902','勘察设计注册石油天然气工程师','default',b'0',23,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008059,0,'national_qualification_type','010903','勘察设计注册采矿/矿物工程师','default',b'0',24,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008060,0,'national_qualification_type','010904','勘察设计注册冶金工程师','default',b'0',25,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008061,0,'national_qualification_type','011001','注册环保工程师','default',b'0',26,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008062,0,'national_qualification_type','011002','注册城市规划师','default',b'0',27,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008063,0,'national_qualification_type','011201','注册安全工程师','default',b'0',28,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008064,0,'national_qualification_type','011301','注册咨询工程师','default',b'0',29,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008065,0,'national_qualification_type','011401','注册房地产估价师','default',b'0',30,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008066,0,'national_qualification_type','011501','注册机械工程师','default',b'0',31,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008067,0,'national_qualification_type','020101','注册会计师','default',b'0',32,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008068,0,'national_qualification_type','030101','注册水利工程师','default',b'0',33,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008069,0,'national_qualification_type','030102','注册水利监理工程师','default',b'0',34,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008070,0,'national_qualification_type','040101','注册测绘师','default',b'0',35,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008071,0,'national_qualification_type','050101','注册核安全工程师','default',b'0',36,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008072,0,'national_qualification_type','050201','注册环评工程师','default',b'0',37,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008073,0,'national_qualification_type','060101','注册税务师','default',b'0',38,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008074,0,'national_qualification_type','070101','注册咨询工程师（投资）','default',b'0',39,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008075,0,'national_qualification_type','080101','国际注册投资分析师','default',b'0',40,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008076,0,'national_qualification_type','090101','注册安全工程师','default',b'0',41,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008077,0,'national_qualification_type','100101','注册设备监理师','default',b'0',42,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008078,0,'national_qualification_type','100301','一级注册计量师','default',b'0',43,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008079,0,'national_qualification_type','100302','二级注册计量师','default',b'0',44,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008080,0,'national_qualification_type','130101','执业药师','default',b'0',45,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008081,0,'national_qualification_type','140101','执业医师','default',b'0',46,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008082,0,'national_qualification_type','140201','执业护士','default',b'0',47,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008083,0,'national_qualification_type','140301','注册营养师','default',b'0',48,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008084,0,'national_qualification_type','150101','执业律师','default',b'0',49,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRestrictedBehavior_119Enum.java → national_restricted_behavior_119（4 条）
(710000000000008085,0,'national_restricted_behavior_119','1','禁止参加招标代理活动','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008086,0,'national_restricted_behavior_119','2','禁止参加投标活动','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008087,0,'national_restricted_behavior_119','3','禁止参加评标、评审活动','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008088,0,'national_restricted_behavior_119','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRestrictedBehavior_125Enum.java → national_restricted_behavior_125（4 条）
(710000000000008089,0,'national_restricted_behavior_125','1','禁止参加招标代理活动','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008090,0,'national_restricted_behavior_125','2','禁止参加投标活动','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008091,0,'national_restricted_behavior_125','3','禁止参加评标活动','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008092,0,'national_restricted_behavior_125','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRestrictedBehavior_138Enum.java → national_restricted_behavior_138（4 条）
(710000000000008093,0,'national_restricted_behavior_138','1','禁止参加招标代理活动','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008094,0,'national_restricted_behavior_138','2','禁止参加投标活动','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008095,0,'national_restricted_behavior_138','3','禁止参加评标活动','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008096,0,'national_restricted_behavior_138','99','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRewardPunishType_118Enum.java → national_reward_punish_type_118（4 条）
(710000000000008097,0,'national_reward_punish_type_118','1','优良评标评审专家','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008098,0,'national_reward_punish_type_118','2','警告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008099,0,'national_reward_punish_type_118','3','撤销专家资格','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008100,0,'national_reward_punish_type_118','9','其他行政处罚或不良行为记录','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRewardPunishType_136Enum.java → national_reward_punish_type_136（12 条）
(710000000000018101,0,'national_reward_punish_type_136','01','优良招标投标主体','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018102,0,'national_reward_punish_type_136','02','优良交易对象','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018103,0,'national_reward_punish_type_136','03','优良从业人员','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018104,0,'national_reward_punish_type_136','04','警告','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008105,0,'national_reward_punish_type_136','05','罚款','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008106,0,'national_reward_punish_type_136','06','没收非法所得、没收非法财物','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008107,0,'national_reward_punish_type_136','07','责令停产停业','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008108,0,'national_reward_punish_type_136','08','暂扣或吊销许可证、执照','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008109,0,'national_reward_punish_type_136','09','行政拘留','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008110,0,'national_reward_punish_type_136','10','降低资质等级','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018111,0,'national_reward_punish_type_136','11','暂停、取消职业资格证书','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018112,0,'national_reward_punish_type_136','99','其他行政处罚或不良行为记录','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSectionClassifyLevel2_36Enum.java → national_section_classify_level2_36（5 条）
(710000000000018113,0,'national_section_classify_level2_36','A-工程','01 规划；02 投资策划与决策；03 勘察；04 设计；05 监理；06 工程造价；07 项目管理(含代建)；08 工程施工；09 其他工程','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018114,0,'national_section_classify_level2_36','B-货物','01 机电产品类；02 医疗器材；03 金属材料；04 石油及其制品；05 煤炭煤层气及其制品；06 化工材料及其制品；07 建筑材料；08 医药；09 其他类','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018115,0,'national_section_classify_level2_36','C-服务','01 勘查与调查；02 公共咨询；03 经济管理；04 工商管理；05 金融；06 法律；07 运维、评估与修理；08 租赁；09 交通运输与物流；10 节能服务；11 高新技术服务；12 其他服务；13 招标代理服务；14 网络、电磁空间安全服务；99 应对气候变化服务','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018116,0,'national_section_classify_level2_36','D-产权','另行制定','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018117,0,'national_section_classify_level2_36','E-土地','另行制定','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSectionClassifyLevel3_37Enum.java → national_section_classify_level3_37（3 条）
(710000000000018118,0,'national_section_classify_level3_37','C-服务','13 招标代理服务（01 工程类代理；02 货物类代理；03 服务类代理；04 产权类代理；05 土地类代理）','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018119,0,'national_section_classify_level3_37','D-产权','另行制定（另行制定）','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018120,0,'national_section_classify_level3_37','E-土地','另行制定（另行制定）','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSectionClassifyLevel4_38Enum.java → national_section_classify_level4_38（3 条）
(710000000000018121,0,'national_section_classify_level4_38','C-服务','13 招标代理服务','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008122,0,'national_section_classify_level4_38','D-产权','另行制定','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008123,0,'national_section_classify_level4_38','E-土地','另行制定','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalStockEconomyTypeEnum.java → national_stock_economy_type（9 条）
(710000000000008124,0,'national_stock_economy_type','1','政府部门、机构','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008125,0,'national_stock_economy_type','2','国有独资公司（企业）','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008126,0,'national_stock_economy_type','3','国有控股企业','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008127,0,'national_stock_economy_type','4','国有事业单位，国有社团等','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008128,0,'national_stock_economy_type','5','国有参股企业','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008129,0,'national_stock_economy_type','6','非国有企业','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008130,0,'national_stock_economy_type','7','外资企业','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008131,0,'national_stock_economy_type','8','国有实际控制企业','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008132,0,'national_stock_economy_type','99','其他','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalStockReportTypeEnum.java → national_stock_report_type（3 条）
(710000000000008133,0,'national_stock_report_type','1','月报','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008134,0,'national_stock_report_type','2','季报','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008135,0,'national_stock_report_type','3','年报','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalStockTradeModeEnum.java → national_stock_trade_mode（6 条）
(710000000000008136,0,'national_stock_trade_mode','1','网络竞价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008137,0,'national_stock_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008138,0,'national_stock_trade_mode','3','招投标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008139,0,'national_stock_trade_mode','4','协议转让（非竞价）','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008140,0,'national_stock_trade_mode','5','动态报价','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008141,0,'national_stock_trade_mode','99','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSubjectCategoryEnum.java → national_subject_category（8 条）
(710000000000008142,0,'national_subject_category','1','自然人','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008143,0,'national_subject_category','2','法人','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008144,0,'national_subject_category','3','联合体','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008145,0,'national_subject_category','4','部队','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008146,0,'national_subject_category','5','港澳企业','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008147,0,'national_subject_category','6','境外企业','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008148,0,'national_subject_category','7','二级单位（非法人）','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008149,0,'national_subject_category','9','其他','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSubjectRoleTypeEnum.java → national_subject_role_type（12 条）
(710000000000008150,0,'national_subject_role_type','01','招标人','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008151,0,'national_subject_role_type','02','招标代理机构','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008152,0,'national_subject_role_type','03','投标人','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008153,0,'national_subject_role_type','04','采购人','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008154,0,'national_subject_role_type','05','采购代理机构','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008155,0,'national_subject_role_type','06','供应商','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008156,0,'national_subject_role_type','07','出让人','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008157,0,'national_subject_role_type','08','受让人','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008158,0,'national_subject_role_type','09','竞得人','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008159,0,'national_subject_role_type','10','意向受让人','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008160,0,'national_subject_role_type','11','意向总得人','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008161,0,'national_subject_role_type','99','其他','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSupplyModeEnum.java → national_supply_mode（3 条）
(710000000000008162,0,'national_supply_mode','1','协议出让','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008163,0,'national_supply_mode','2','招拍挂出让','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008164,0,'national_supply_mode','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTaxCertTypeEnum.java → national_tax_cert_type（3 条）
(710000000000008165,0,'national_tax_cert_type','1','国税','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008166,0,'national_tax_cert_type','2','地税','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008167,0,'national_tax_cert_type','3','国税和地税','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTenderModeEnum.java → national_tender_mode（3 条）
(710000000000008168,0,'national_tender_mode','1','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008169,0,'national_tender_mode','2','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008170,0,'national_tender_mode','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTenderOrganizeFormEnum.java → national_tender_organize_form（3 条）
(710000000000008171,0,'national_tender_organize_form','1','自行招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008172,0,'national_tender_organize_form','2','委托招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008173,0,'national_tender_organize_form','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTradeModeEnum.java → national_trade_mode（12 条）
(710000000000008174,0,'national_trade_mode','01','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008175,0,'national_trade_mode','02','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008176,0,'national_trade_mode','04','竞争性谈判','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008177,0,'national_trade_mode','05','询价采购','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008178,0,'national_trade_mode','06','单一来源采购','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008179,0,'national_trade_mode','07','挂牌','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008180,0,'national_trade_mode','08','拍卖','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008181,0,'national_trade_mode','09','协议转让','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008182,0,'national_trade_mode','10','竞价','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008183,0,'national_trade_mode','11','直接发包','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008184,0,'national_trade_mode','12','竞争性磋商','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008185,0,'national_trade_mode','99','其他','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTransferModeEnum.java → national_transfer_mode（10 条）
(710000000000008186,0,'national_transfer_mode','131','探矿权招拍挂招标出让','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008187,0,'national_transfer_mode','132','探矿权招拍挂拍卖出让','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008188,0,'national_transfer_mode','133','探矿权招拍挂挂牌出让','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008189,0,'national_transfer_mode','210','探矿权转采矿权','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008190,0,'national_transfer_mode','220','采矿权招拍挂招标出让','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008191,0,'national_transfer_mode','230','采矿权招拍挂拍卖出让','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008192,0,'national_transfer_mode','240','采矿权招拍挂挂牌出让','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008193,0,'national_transfer_mode','250','协议出让','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008194,0,'national_transfer_mode','260','申请在先','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008195,0,'national_transfer_mode','999','其他','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalYesNoEnum.java → national_yes_no（2 条）
(710000000000008196,0,'national_yes_no','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008197,0,'national_yes_no','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalYesNoStateOwnedEnum.java → national_yes_no_state_owned（2 条）
(710000000000008198,0,'national_yes_no_state_owned','T','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008199,0,'national_yes_no_state_owned','F','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAbnormalSectionTypeEnum.java → shaanxi_abnormal_section_type（4 条）
(710000000000008200,0,'shaanxi_abnormal_section_type','0','重新招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018201,0,'shaanxi_abnormal_section_type','2','终止招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018202,0,'shaanxi_abnormal_section_type','3','延后变更(变更招标方式)','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018203,0,'shaanxi_abnormal_section_type','5','重新评标','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAbnormalTypeEnum.java → shaanxi_abnormal_type（4 条）
(710000000000018204,0,'shaanxi_abnormal_type','1','现场变更','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008205,0,'shaanxi_abnormal_type','2','重新招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008206,0,'shaanxi_abnormal_type','3','延后变更','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008207,0,'shaanxi_abnormal_type','4','终止招标','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAmountUnit_1Enum.java → shaanxi_amount_unit_1（2 条）
(710000000000008208,0,'shaanxi_amount_unit_1','0','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008209,0,'shaanxi_amount_unit_1','1','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAmountUnit_2Enum.java → shaanxi_amount_unit_2（2 条）
(710000000000008210,0,'shaanxi_amount_unit_2','0','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018211,0,'shaanxi_amount_unit_2','1','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAmountUnit_56Enum.java → shaanxi_amount_unit_56（2 条）
(710000000000018212,0,'shaanxi_amount_unit_56','0','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018213,0,'shaanxi_amount_unit_56','1','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAssetTypeEnum.java → shaanxi_asset_type（6 条）
(710000000000018214,0,'shaanxi_asset_type','1','房产','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018215,0,'shaanxi_asset_type','2','土地使用权','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008216,0,'shaanxi_asset_type','3','交通运输工具','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008217,0,'shaanxi_asset_type','4','设备','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008218,0,'shaanxi_asset_type','5','在建工程','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008219,0,'shaanxi_asset_type','99','其他资产','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAuctionType_3Enum.java → shaanxi_auction_type_3（6 条）
(710000000000008220,0,'shaanxi_auction_type_3','0','招标出让','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008221,0,'shaanxi_auction_type_3','1','挂牌出让','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008222,0,'shaanxi_auction_type_3','2','拍卖出让','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008223,0,'shaanxi_auction_type_3','3','网上挂牌出让','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008224,0,'shaanxi_auction_type_3','4','网上拍卖出让','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008225,0,'shaanxi_auction_type_3','9','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBidCurrencyUnit_3Enum.java → shaanxi_bid_currency_unit_3（2 条）
(710000000000008226,0,'shaanxi_bid_currency_unit_3','1','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008227,0,'shaanxi_bid_currency_unit_3','2','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBidCurrencyUnit_413Enum.java → shaanxi_bid_currency_unit_413（2 条）
(710000000000008228,0,'shaanxi_bid_currency_unit_413','1','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008229,0,'shaanxi_bid_currency_unit_413','2','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBidStepTypeEnum.java → shaanxi_bid_step_type（2 条）
(710000000000008230,0,'shaanxi_bid_step_type','1','可变步长','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008231,0,'shaanxi_bid_step_type','2','固定步长','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBidTradeModeEnum.java → shaanxi_bid_trade_mode（6 条）
(710000000000008232,0,'shaanxi_bid_trade_mode','1','招投标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008233,0,'shaanxi_bid_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008234,0,'shaanxi_bid_trade_mode','3','挂牌','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008235,0,'shaanxi_bid_trade_mode','250','协议出让','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008236,0,'shaanxi_bid_trade_mode','260','申请在先','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008237,0,'shaanxi_bid_trade_mode','999','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBiddingSituationEnum.java → shaanxi_bidding_situation（3 条）
(710000000000008238,0,'shaanxi_bidding_situation','1','国有资金控股或占主导地位的依法招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008239,0,'shaanxi_bidding_situation','2','其他情形的依法招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008240,0,'shaanxi_bidding_situation','3','自愿招标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBusinessScaleEnum.java → shaanxi_business_scale（3 条）
(710000000000008241,0,'shaanxi_business_scale','01','大型','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008242,0,'shaanxi_business_scale','02','中型','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008243,0,'shaanxi_business_scale','03','小型','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiChallengeStageEnum.java → shaanxi_challenge_stage（5 条）
(710000000000008244,0,'shaanxi_challenge_stage','01','资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008245,0,'shaanxi_challenge_stage','02','采购文件','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008246,0,'shaanxi_challenge_stage','03','开标过程','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008247,0,'shaanxi_challenge_stage','04','资格预审结果','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008248,0,'shaanxi_challenge_stage','05','评标结果','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiCircuitBreakerTypeEnum.java → shaanxi_circuit_breaker_type（2 条）
(710000000000008249,0,'shaanxi_circuit_breaker_type','0','流标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008250,0,'shaanxi_circuit_breaker_type','1','新指标竞价','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiCommitteeRole_1Enum.java → shaanxi_committee_role_1（3 条）
(710000000000008251,0,'shaanxi_committee_role_1','0','技术标评委','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008252,0,'shaanxi_committee_role_1','1','经济标评委','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008253,0,'shaanxi_committee_role_1','2','经济技术标评委','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiCommitteeRole_2Enum.java → shaanxi_committee_role_2（3 条）
(710000000000008254,0,'shaanxi_committee_role_2','0','技术标评委','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008255,0,'shaanxi_committee_role_2','1','经济标评委','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008256,0,'shaanxi_committee_role_2','2','经济技术标评委','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiComplaintMeasureEnum.java → shaanxi_complaint_measure（4 条）
(710000000000008257,0,'shaanxi_complaint_measure','1','项目暂停','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008258,0,'shaanxi_complaint_measure','2','项目终止','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008259,0,'shaanxi_complaint_measure','3','项目流标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008260,0,'shaanxi_complaint_measure','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiComplianceStatusEnum.java → shaanxi_compliance_status（3 条）
(710000000000008261,0,'shaanxi_compliance_status','1','合格','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008262,0,'shaanxi_compliance_status','2','不合格','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008263,0,'shaanxi_compliance_status','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiContinueQuoteAllowedEnum.java → shaanxi_continue_quote_allowed（2 条）
(710000000000008264,0,'shaanxi_continue_quote_allowed','1','允许','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008265,0,'shaanxi_continue_quote_allowed','0','不允许','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiDepositStatus_3Enum.java → shaanxi_deposit_status_3（2 条）
(710000000000008266,0,'shaanxi_deposit_status_3','1','缴纳','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008267,0,'shaanxi_deposit_status_3','0','未缴纳','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiDepositStatus_413Enum.java → shaanxi_deposit_status_413（2 条）
(710000000000008268,0,'shaanxi_deposit_status_413','1','缴纳','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008269,0,'shaanxi_deposit_status_413','0','未缴纳','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiDissentTypeEnum.java → shaanxi_dissent_type（5 条）
(710000000000008270,0,'shaanxi_dissent_type','1','资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008271,0,'shaanxi_dissent_type','2','招标文件','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008272,0,'shaanxi_dissent_type','3','开标过程','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008273,0,'shaanxi_dissent_type','4','资格预审结果','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008274,0,'shaanxi_dissent_type','5','评标结果','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEconomyTypeEnum.java → shaanxi_economy_type（9 条）
(710000000000008275,0,'shaanxi_economy_type','1','政府部门、机构','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008276,0,'shaanxi_economy_type','2','国有独资公司（企业）','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008277,0,'shaanxi_economy_type','3','国有控股企业','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008278,0,'shaanxi_economy_type','4','国有事业单位，国有社团等','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008279,0,'shaanxi_economy_type','5','国有参股企业','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008280,0,'shaanxi_economy_type','6','非国有企业','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008281,0,'shaanxi_economy_type','7','外资企业','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008282,0,'shaanxi_economy_type','8','国有实际控制企业','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008283,0,'shaanxi_economy_type','99','其他','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEducationEnum.java → shaanxi_education（7 条）
(710000000000008284,0,'shaanxi_education','博士','博士','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008285,0,'shaanxi_education','硕士','硕士','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008286,0,'shaanxi_education','本科','本科','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008287,0,'shaanxi_education','大专','大专','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008288,0,'shaanxi_education','中专','中专','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008289,0,'shaanxi_education','高中','高中','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008290,0,'shaanxi_education','初中','初中','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEnrollModeEnum.java → shaanxi_enroll_mode（4 条）
(710000000000008291,0,'shaanxi_enroll_mode','1','线上','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008292,0,'shaanxi_enroll_mode','2','代理处','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008293,0,'shaanxi_enroll_mode','3','招标办','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008294,0,'shaanxi_enroll_mode','5','交易中心','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEvalDurationEnum.java → shaanxi_eval_duration（6 条）
(710000000000008295,0,'shaanxi_eval_duration','1','半天','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008296,0,'shaanxi_eval_duration','2','半天或一天','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008297,0,'shaanxi_eval_duration','3','一天','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008298,0,'shaanxi_eval_duration','4','一天或两天','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008299,0,'shaanxi_eval_duration','5','两天','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008300,0,'shaanxi_eval_duration','6','两天以上','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEvaluationResultEnum.java → shaanxi_evaluation_result（3 条）
(710000000000008301,0,'shaanxi_evaluation_result','1','招标失败','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008302,0,'shaanxi_evaluation_result','2','重新招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008303,0,'shaanxi_evaluation_result','3','评标结果公示','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiExpertTypeEnum.java → shaanxi_expert_type（2 条）
(710000000000008304,0,'shaanxi_expert_type','1','在库专家','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008305,0,'shaanxi_expert_type','2','招标人代表','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiExtractCategoryEnum.java → shaanxi_extract_category（3 条）
(710000000000008306,0,'shaanxi_extract_category','0','本地抽取','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008307,0,'shaanxi_extract_category','1','全省抽取','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008308,0,'shaanxi_extract_category','2','客场抽取','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiExtractLevelEnum.java → shaanxi_extract_level（3 条）
(710000000000008309,0,'shaanxi_extract_level','0','资深评委','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008310,0,'shaanxi_extract_level','1','应急评委','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008311,0,'shaanxi_extract_level','2','备选库专家','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiFileType_1Enum.java → shaanxi_file_type_1（2 条）
(710000000000008312,0,'shaanxi_file_type_1','1','招标文件/资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008313,0,'shaanxi_file_type_1','2','招标文件澄清与修改/资格预审文件澄清与修改','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiFileType_2Enum.java → shaanxi_file_type_2（2 条）
(710000000000008314,0,'shaanxi_file_type_2','1','采购文件/资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008315,0,'shaanxi_file_type_2','2','采购文件澄清与修改/资格预审文件澄清与修改','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiHandleDecisionEnum.java → shaanxi_handle_decision（4 条）
(710000000000008316,0,'shaanxi_handle_decision','0','重新招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008317,0,'shaanxi_handle_decision','2','终止招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008318,0,'shaanxi_handle_decision','3','延后变更（变更招标方式）','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008319,0,'shaanxi_handle_decision','5','重新评标','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiIdCardTypeEnum.java → shaanxi_id_card_type（27 条）
(710000000000008320,0,'shaanxi_id_card_type','01','居民身份证','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008321,0,'shaanxi_id_card_type','02','军官证','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008322,0,'shaanxi_id_card_type','03','武警警官证','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008323,0,'shaanxi_id_card_type','04','士兵证','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008324,0,'shaanxi_id_card_type','05','军队离退休干部证','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008325,0,'shaanxi_id_card_type','06','残疾人证','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008326,0,'shaanxi_id_card_type','07','残疾军人证（1-8级）','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008327,0,'shaanxi_id_card_type','08','护照','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008328,0,'shaanxi_id_card_type','09','港澳同胞回乡证','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008329,0,'shaanxi_id_card_type','10','港澳居民来往内地通行证','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008330,0,'shaanxi_id_card_type','11','中华人民共和国往来港澳通行证','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008331,0,'shaanxi_id_card_type','12','台湾居民来往大陆通行证','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008332,0,'shaanxi_id_card_type','13','大陆居民往来台湾通行证','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008333,0,'shaanxi_id_card_type','14','外国人居留证','default',b'0',14,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008334,0,'shaanxi_id_card_type','15','外交官证','default',b'0',15,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008335,0,'shaanxi_id_card_type','16','领事馆证','default',b'0',16,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008336,0,'shaanxi_id_card_type','17','海员证','default',b'0',17,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008337,0,'shaanxi_id_card_type','18','香港身份证','default',b'0',18,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008338,0,'shaanxi_id_card_type','19','台湾身份证','default',b'0',19,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008339,0,'shaanxi_id_card_type','20','澳门身份证','default',b'0',20,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008340,0,'shaanxi_id_card_type','21','外国人身份证件','default',b'0',21,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008341,0,'shaanxi_id_card_type','22','高校毕业生自主创业证','default',b'0',22,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008342,0,'shaanxi_id_card_type','23','就业失业登记证','default',b'0',23,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008343,0,'shaanxi_id_card_type','24','台胞证','default',b'0',24,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008344,0,'shaanxi_id_card_type','25','退休证','default',b'0',25,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008345,0,'shaanxi_id_card_type','26','离休证','default',b'0',26,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008346,0,'shaanxi_id_card_type','99','其他证件','default',b'0',27,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiInquiryTypeEnum.java → shaanxi_inquiry_type（2 条）
(710000000000008347,0,'shaanxi_inquiry_type','1','资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008348,0,'shaanxi_inquiry_type','2','招标文件','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiInternalDecisionEnum.java → shaanxi_internal_decision（5 条）
(710000000000008349,0,'shaanxi_internal_decision','1','股东会决议','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008350,0,'shaanxi_internal_decision','2','董事会决议','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008351,0,'shaanxi_internal_decision','4','总经理办公会议决议','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008352,0,'shaanxi_internal_decision','5','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008353,0,'shaanxi_internal_decision','6','行政会议决议','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiJudgeTypeEnum.java → shaanxi_judge_type（2 条）
(710000000000008354,0,'shaanxi_judge_type','0','采购人代表','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008355,0,'shaanxi_judge_type','1','邀请评委','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiMarginPayTypeEnum.java → shaanxi_margin_pay_type（7 条）
(710000000000008356,0,'shaanxi_margin_pay_type','1','资金现金','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008357,0,'shaanxi_margin_pay_type','2','银行保函','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008358,0,'shaanxi_margin_pay_type','3','担保','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008359,0,'shaanxi_margin_pay_type','4','电汇','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008360,0,'shaanxi_margin_pay_type','5','汇票','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008361,0,'shaanxi_margin_pay_type','6','支票','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008362,0,'shaanxi_margin_pay_type','9','其他','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiMarginPayType_2Enum.java → shaanxi_margin_pay_type_2（7 条）
(710000000000008363,0,'shaanxi_margin_pay_type_2','1','银行转账','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008364,0,'shaanxi_margin_pay_type_2','2','银行保函','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008365,0,'shaanxi_margin_pay_type_2','3','担保','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008366,0,'shaanxi_margin_pay_type_2','4','电汇','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008367,0,'shaanxi_margin_pay_type_2','5','汇票','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008368,0,'shaanxi_margin_pay_type_2','6','支票','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008369,0,'shaanxi_margin_pay_type_2','9','其他','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiMiningAreaCategoryEnum.java → shaanxi_mining_area_category（2 条）
(710000000000008370,0,'shaanxi_mining_area_category','01','采矿权','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008371,0,'shaanxi_mining_area_category','02','探矿权','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiMiningMethodEnum.java → shaanxi_mining_method（3 条）
(710000000000008372,0,'shaanxi_mining_method','01','露天开采','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008373,0,'shaanxi_mining_method','02','地下开采','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008374,0,'shaanxi_mining_method','03','地露结合开采','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiNoticeNatureEnum.java → shaanxi_notice_nature（4 条）
(710000000000008375,0,'shaanxi_notice_nature','1','正常公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008376,0,'shaanxi_notice_nature','2','更正公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008377,0,'shaanxi_notice_nature','3','重发公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008378,0,'shaanxi_notice_nature','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiNoticeTypeEnum.java → shaanxi_notice_type（4 条）
(710000000000008379,0,'shaanxi_notice_type','1','招标公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008380,0,'shaanxi_notice_type','2','资格预审公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008381,0,'shaanxi_notice_type','3','中标结果公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008382,0,'shaanxi_notice_type','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiNoticeType_2Enum.java → shaanxi_notice_type_2（8 条）
(710000000000008383,0,'shaanxi_notice_type_2','1','采购公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008384,0,'shaanxi_notice_type_2','2','资格预审公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008385,0,'shaanxi_notice_type_2','3','采购更正公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008386,0,'shaanxi_notice_type_2','4','结果公告','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008387,0,'shaanxi_notice_type_2','5','结果更正公告','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008388,0,'shaanxi_notice_type_2','6','中止公告','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008389,0,'shaanxi_notice_type_2','7','终止公告','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008390,0,'shaanxi_notice_type_2','8','其他公告','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPreQualifyMode_2Enum.java → shaanxi_pre_qualify_mode_2（2 条）
(710000000000008391,0,'shaanxi_pre_qualify_mode_2','1','资格预审','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008392,0,'shaanxi_pre_qualify_mode_2','2','资格后审','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPreQualifyResult_1Enum.java → shaanxi_pre_qualify_result_1（2 条）
(710000000000008393,0,'shaanxi_pre_qualify_result_1','1','资审通过','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008394,0,'shaanxi_pre_qualify_result_1','2','资审不通过','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPreQualifyResult_2Enum.java → shaanxi_pre_qualify_result_2（2 条）
(710000000000008395,0,'shaanxi_pre_qualify_result_2','1','资审通过','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008396,0,'shaanxi_pre_qualify_result_2','2','资审不通过','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPriceFormEnum.java → shaanxi_price_form（4 条）
(710000000000008397,0,'shaanxi_price_form','1','金额','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008398,0,'shaanxi_price_form','2','费率/比率/优惠率/合格率等','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008399,0,'shaanxi_price_form','3','单价','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008400,0,'shaanxi_price_form','9','其他形式','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPriceForm_2Enum.java → shaanxi_price_form_2（4 条）
(710000000000008401,0,'shaanxi_price_form_2','1','金额','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008402,0,'shaanxi_price_form_2','2','费率/比率/优惠率/合格率等','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008403,0,'shaanxi_price_form_2','3','单价','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008404,0,'shaanxi_price_form_2','9','其他形式','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiProfessionalQualificationEnum.java → shaanxi_professional_qualification（12 条）
(710000000000008405,0,'shaanxi_professional_qualification','01','注册建筑师','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008406,0,'shaanxi_professional_qualification','02','注册结构工程师','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008407,0,'shaanxi_professional_qualification','03','注册工程师','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008408,0,'shaanxi_professional_qualification','04','注册建造师','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008409,0,'shaanxi_professional_qualification','05','注册监理工程师','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008410,0,'shaanxi_professional_qualification','06','注册造价工程师','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008411,0,'shaanxi_professional_qualification','07','造价工程师','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008412,0,'shaanxi_professional_qualification','08','注册结构工程师','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008413,0,'shaanxi_professional_qualification','09','注册土木工程师','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008414,0,'shaanxi_professional_qualification','10','监理工程师','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008415,0,'shaanxi_professional_qualification','11','施工员','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008416,0,'shaanxi_professional_qualification','12','全国公路水运工程试验检测专业技术人员','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiProjectTradeCategoryEnum.java → shaanxi_project_trade_category（12 条）
(710000000000008417,0,'shaanxi_project_trade_category','A','房屋建筑工程','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008418,0,'shaanxi_project_trade_category','B','市政基础设施工程','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008419,0,'shaanxi_project_trade_category','GD','轨道交通工程','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008420,0,'shaanxi_project_trade_category','F','水利工程','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008421,0,'shaanxi_project_trade_category','L','电力工程','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008422,0,'shaanxi_project_trade_category','E','公路工程','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008423,0,'shaanxi_project_trade_category','D','园林绿化','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008424,0,'shaanxi_project_trade_category','Y','其他工程','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008425,0,'shaanxi_project_trade_category','TL','铁路工程','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008426,0,'shaanxi_project_trade_category','TX','通讯工程','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008427,0,'shaanxi_project_trade_category','GLYH','公路养护','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008428,0,'shaanxi_project_trade_category','WZB','外资办','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPropertyTradeModeEnum.java → shaanxi_property_trade_mode（4 条）
(710000000000008429,0,'shaanxi_property_trade_mode','1','网络竞价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008430,0,'shaanxi_property_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008431,0,'shaanxi_property_trade_mode','3','招投标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008432,0,'shaanxi_property_trade_mode','99','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPublicityTypeEnum.java → shaanxi_publicity_type（3 条）
(710000000000008433,0,'shaanxi_publicity_type','1','正常','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008434,0,'shaanxi_publicity_type','2','更正','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008435,0,'shaanxi_publicity_type','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPurchaseMode_2Enum.java → shaanxi_purchase_mode_2（7 条）
(710000000000008436,0,'shaanxi_purchase_mode_2','公','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008437,0,'shaanxi_purchase_mode_2','邀','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008438,0,'shaanxi_purchase_mode_2','竞','竞争性谈判','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008439,0,'shaanxi_purchase_mode_2','单','单一来源采购','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008440,0,'shaanxi_purchase_mode_2','询','询价','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008441,0,'shaanxi_purchase_mode_2','磋','竞争性磋商','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008442,0,'shaanxi_purchase_mode_2','框','框架协议采购','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPurchaseOrganizeForm_2Enum.java → shaanxi_purchase_organize_form_2（3 条）
(710000000000008443,0,'shaanxi_purchase_organize_form_2','1','集中采购','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008444,0,'shaanxi_purchase_organize_form_2','2','分散采购','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008445,0,'shaanxi_purchase_organize_form_2','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPurchasePartyTypeEnum.java → shaanxi_purchase_party_type（8 条）
(710000000000008446,0,'shaanxi_purchase_party_type','1','采购人','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008447,0,'shaanxi_purchase_party_type','2','采购代理机构','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008448,0,'shaanxi_purchase_party_type','21','集中采购机构','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008449,0,'shaanxi_purchase_party_type','22','部门集中采购机构','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008450,0,'shaanxi_purchase_party_type','23','政府采购代理机构','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008451,0,'shaanxi_purchase_party_type','3','评审专家','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008452,0,'shaanxi_purchase_party_type','4','供应商','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008453,0,'shaanxi_purchase_party_type','9','其他','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPurchaseTradeCategoryEnum.java → shaanxi_purchase_trade_category（3 条）
(710000000000008454,0,'shaanxi_purchase_trade_category','D01','货物类（含药品集中采购）','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008455,0,'shaanxi_purchase_trade_category','D02','工程类','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008456,0,'shaanxi_purchase_trade_category','D03','服务类','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiQualifyCheckModeEnum.java → shaanxi_qualify_check_mode（2 条）
(710000000000008457,0,'shaanxi_qualify_check_mode','1','资格预审','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008458,0,'shaanxi_qualify_check_mode','2','资格后审','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiQualityLevelEnum.java → shaanxi_quality_level（7 条）
(710000000000008459,0,'shaanxi_quality_level','1','合格','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008460,0,'shaanxi_quality_level','2','市优','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008461,0,'shaanxi_quality_level','3','省优','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008462,0,'shaanxi_quality_level','4','国优','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008463,0,'shaanxi_quality_level','5','优良','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008464,0,'shaanxi_quality_level','6','符合国家验收标准','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008465,0,'shaanxi_quality_level','7','满足国标合格条件','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiQuoteFormEnum.java → shaanxi_quote_form（2 条）
(710000000000008466,0,'shaanxi_quote_form','1','总价报价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008467,0,'shaanxi_quote_form','2','费率报价','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiSectionCategoryEnum.java → shaanxi_section_category（18 条）
(710000000000008468,0,'shaanxi_section_category','A','货物类','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008469,0,'shaanxi_section_category','A03','一般设备','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008470,0,'shaanxi_section_category','A0301','电器设备','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008471,0,'shaanxi_section_category','A0302','办公自动化设备','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008472,0,'shaanxi_section_category','A0303','家俱','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008473,0,'shaanxi_section_category','A06','物资','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008474,0,'shaanxi_section_category','A07','专用材料','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008475,0,'shaanxi_section_category','A10','专用设备','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008476,0,'shaanxi_section_category','A11','交通工具及配件','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008477,0,'shaanxi_section_category','B','工程类','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008478,0,'shaanxi_section_category','B01','建筑工程','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008479,0,'shaanxi_section_category','B02','市政建设工程','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008480,0,'shaanxi_section_category','C','服务类','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008481,0,'shaanxi_section_category','C01','印刷','default',b'0',14,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008482,0,'shaanxi_section_category','C02','办公用纸','default',b'0',15,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008483,0,'shaanxi_section_category','C03','软件开发设计','default',b'0',16,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008484,0,'shaanxi_section_category','C04','网络建设或维护及系统集成','default',b'0',17,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008485,0,'shaanxi_section_category','C05','其他服务','default',b'0',18,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiSectionClassifyLevel2_1Enum.java → shaanxi_section_classify_level2_1（5 条）
(710000000000008486,0,'shaanxi_section_classify_level2_1','A-工程','01 规划；02 投资策划与决策；03 勘察；04 设计；05 监理；06 工程造价；07 项目管理(含代建)；08 工程施工；09 其他工程；10 房建；11 交通组别；12 交通标段；13 水利','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008487,0,'shaanxi_section_classify_level2_1','B-货物','01 机电产品类；02 医疗器材；03 金属材料；04 石油及其制品；05 煤炭煤层气及其制品；06 化工材料及其制品；07 建筑材料；08 医药；09其他类','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008488,0,'shaanxi_section_classify_level2_1','C-服务','01 勘查与调查；02 公共咨询；03 经济管理；04 工商管理；05 金融；06 法律；07 运维、评估与修理；08 租赁；09 交通运输与物流；10 节能服务；11 高新技术服务；12 其他服务；13招标代理服务；14网络、电磁空间安全服务；99应对气候变化服务','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008489,0,'shaanxi_section_classify_level2_1','D-产权','另行制定','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008490,0,'shaanxi_section_classify_level2_1','E-土地','另行制定','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiSectionClassifyLevel3_1Enum.java → shaanxi_section_classify_level3_1（4 条）
(710000000000008491,0,'shaanxi_section_classify_level3_1','A-工程','10 房建市政；11 交通组别；12 交通标段；13 水利','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008492,0,'shaanxi_section_classify_level3_1','C-服务','13 招标代理服务；','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008493,0,'shaanxi_section_classify_level3_1','D-产权','另行制定','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008494,0,'shaanxi_section_classify_level3_1','E-土地','另行制定','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiSectionClassifyLevel4_1Enum.java → shaanxi_section_classify_level4_1（3 条）
(710000000000008495,0,'shaanxi_section_classify_level4_1','C-服务','13 招标代理服务','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008496,0,'shaanxi_section_classify_level4_1','D-产权','另行制定','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008497,0,'shaanxi_section_classify_level4_1','E-土地','另行制定','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiTenderModeEnum.java → shaanxi_tender_mode（3 条）
(710000000000008498,0,'shaanxi_tender_mode','1','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008499,0,'shaanxi_tender_mode','2','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008500,0,'shaanxi_tender_mode','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiTenderOrganizeFormEnum.java → shaanxi_tender_organize_form（3 条）
(710000000000008501,0,'shaanxi_tender_organize_form','1','自行招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008502,0,'shaanxi_tender_organize_form','2','委托招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008503,0,'shaanxi_tender_organize_form','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiTradeModeEnum.java → shaanxi_trade_mode（8 条）
(710000000000008504,0,'shaanxi_trade_mode','1','网络竞价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008505,0,'shaanxi_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008506,0,'shaanxi_trade_mode','3','招投标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008507,0,'shaanxi_trade_mode','4','协议转让（非竞价）','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008508,0,'shaanxi_trade_mode','5','动态报价','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008509,0,'shaanxi_trade_mode','6','谈判','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008510,0,'shaanxi_trade_mode','7','综合评议','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008511,0,'shaanxi_trade_mode','99','其他','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiVenueTypeEnum.java → shaanxi_venue_type（5 条）
(710000000000008512,0,'shaanxi_venue_type','1','资格审查评审室','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008513,0,'shaanxi_venue_type','2','开标室','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008514,0,'shaanxi_venue_type','3','评标室','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008515,0,'shaanxi_venue_type','4','抽签室','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008516,0,'shaanxi_venue_type','5','不见面开标室','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_1Enum.java → shaanxi_yes_no_1（2 条）
(710000000000008517,0,'shaanxi_yes_no_1','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008518,0,'shaanxi_yes_no_1','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_2Enum.java → shaanxi_yes_no_2（2 条）
(710000000000008519,0,'shaanxi_yes_no_2','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008520,0,'shaanxi_yes_no_2','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_3Enum.java → shaanxi_yes_no_3（2 条）
(710000000000008521,0,'shaanxi_yes_no_3','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008522,0,'shaanxi_yes_no_3','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_413Enum.java → shaanxi_yes_no_413（2 条）
(710000000000008523,0,'shaanxi_yes_no_413','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008524,0,'shaanxi_yes_no_413','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_56Enum.java → shaanxi_yes_no_56（2 条）
(710000000000008525,0,'shaanxi_yes_no_56','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008526,0,'shaanxi_yes_no_56','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');
