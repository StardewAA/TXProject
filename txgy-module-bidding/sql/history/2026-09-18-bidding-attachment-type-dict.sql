-- =====================================================================
-- 2026-09-18 附件类型字典（attachment_type）注册 —— 35 类全集
-- 依据：docs/前端规范-全域接口说明/29-bidding通用附件域与关注域-前端契约-2026-09-27.md §2.6
-- 幂等：INSERT IGNORE（uk_dict 唯一键：tenant_id+dict_type+dict_value+deleted）
-- id 段位：710000000000005001 起（50xx 段=附件域，不与既有 0001-46xx 撞号）
-- 镜像待办（2026-09-18 修正）：字典统一加入 sql/tools/build-credential-sync.js DDL_SOURCES 后重建 master-credential-sync.sql；
--   bidding_attachment 建表才手工并入 master-bidding-sync.sql（契约代理批次，见待落地清单 §6.1）
-- =====================================================================
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000005001,0,'attachment_type','PROJECT_APPROVAL_FILE','项目审批文件','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005002,0,'attachment_type','TENDER_FILE','招标文件','default',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005003,0,'attachment_type','TENDER_FILE_ATTACHS','招标文件附件','default',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005004,0,'attachment_type','TENDER_NOTICE','招标公告','info',b'0',4,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005005,0,'attachment_type','OTHER_FILE','其他文件','default',b'0',5,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005006,0,'attachment_type','FILE_EX_TDOC','电子文档（扩展）','default',b'0',6,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005007,0,'attachment_type','INVITE_DOC','邀请函','info',b'0',7,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005008,0,'attachment_type','PUBLICITY_DOC','公示文件','info',b'0',8,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005009,0,'attachment_type','WIN_BID_NOTICE','中标通知','success',b'0',9,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005010,0,'attachment_type','CONTRACT_DOC','合同','info',b'0',10,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005011,0,'attachment_type','QUALIFICATION_DOC','资格文件','default',b'0',11,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005012,0,'attachment_type','PROJECT_SOURCE_PROOF','项目来源证明','default',b'0',12,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005013,0,'attachment_type','Contract_FILE','合同文件（规范拼写原样）','info',b'0',13,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005014,0,'attachment_type','TENDER_CHANGE_NOTICE','招标变更通知','warning',b'0',14,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005015,0,'attachment_type','QUALIFICATION_NOTICE','资格审查通知','info',b'0',15,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005016,0,'attachment_type','QUALIFICATION_CLARIFY_FILE','资格审查澄清文件','default',b'0',16,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005017,0,'attachment_type','QUALIFICATION_LIST','资格审查名单','info',b'0',17,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005018,0,'attachment_type','BID_SIGN_LIST','开标签到表','default',b'0',18,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005019,0,'attachment_type','QUALIFICATION_REPORT','资格审查报告','info',b'0',19,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005020,0,'attachment_type','QUALIFICATION_RESULT_NOTICE','资格审查结果公示','success',b'0',20,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005021,0,'attachment_type','NET_QUESTION','网上提问','default',b'0',21,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005022,0,'attachment_type','SURVEY_RECORD','勘察记录','default',b'0',22,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005023,0,'attachment_type','CONTROL_FILE','控制价文件','default',b'0',23,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005024,0,'attachment_type','WIN_NOTICE_FILE','中标通知书文件','success',b'0',24,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005025,0,'attachment_type','APPROACH_PROVE','进场证明','default',b'0',25,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005026,0,'attachment_type','EXPERT_REVIEW_FILE','专家评审文件','info',b'0',26,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005027,0,'attachment_type','COMMENTS_FILE','评标意见文件','info',b'0',27,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005028,0,'attachment_type','QUANTITIES_CLEAR_REPORT','工程量清单澄清报告','default',b'0',28,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005029,0,'attachment_type','WIN_BID_TENDER_FILE','中标人投标文件','success',b'0',29,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005030,0,'attachment_type','EVALUATION_REPORT','评标报告','info',b'0',30,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005031,0,'attachment_type','CONTRACT_SING_FILE','合同签订文件','info',b'0',31,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005032,0,'attachment_type','PERFORMANCE_FILE','履约/业绩文件','default',b'0',32,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005033,0,'attachment_type','INQUIRY_FILE','质询文件','warning',b'0',33,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005034,0,'attachment_type','OBJECTION_FILE','异议函','warning',b'0',34,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005035,0,'attachment_type','COMPLAIN_FILE','投诉函','warning',b'0',35,b'1',NULL,'1',NOW(),'1',NOW(),b'0');
