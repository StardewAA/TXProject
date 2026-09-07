package cn.iocoder.txgy.module.bidding.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * bidding 的错误码。
 * 段位从 1-060-xxx-xxx 开始，按业务区块往下排，别跨段乱用。
 */
public interface ErrorCodeConstants {

    // ========== 项目/招标项目/标段 1-060-100-000 ==========
    ErrorCode PROJECT_NOT_EXISTS = new ErrorCode(1_060_100_000, "项目不存在");
    ErrorCode TENDER_PROJECT_NOT_EXISTS = new ErrorCode(1_060_100_001, "招标项目不存在");
    ErrorCode BID_SECTION_NOT_EXISTS = new ErrorCode(1_060_100_002, "标段（包）不存在");
    ErrorCode BID_SECTION_CODE_DUPLICATE = new ErrorCode(1_060_100_003, "标段（包）编号已存在");

    // ========== 公告/文件 1-060-101-000 ==========
    ErrorCode NOTICE_NOT_EXISTS = new ErrorCode(1_060_101_000, "公告不存在");
    ErrorCode NOTICE_SEND_TIME_INVALID = new ErrorCode(1_060_101_001, "公告发布时间不合法");
    ErrorCode NOTICE_STATUS_TRANSITION_INVALID = new ErrorCode(1_060_101_002, "公告状态流转不允许（草稿→发布→撤下/重发→归档）");
    ErrorCode NOTICE_PAYLOAD_INVALID = new ErrorCode(1_060_101_003, "公告字段不符合权威数据集：{0}");
    ErrorCode NOTICE_CONTENT_REQUIRED = new ErrorCode(1_060_101_004, "发布前必须存在公告正文");
    ErrorCode NOTICE_SEARCH_UNAVAILABLE = new ErrorCode(1_060_101_005, "关键词检索不可用：请启用检索组件（bidding.es.enabled=true）");
    ErrorCode NOTICE_PDF_REQUIRED = new ErrorCode(1_060_101_006, "发布前必须保存公告 PDF 原文件（pdfFileId）");
    ErrorCode NOTICE_GATHER_ONLY_PUBLIC = new ErrorCode(1_060_101_007, "公开同步只允许已发布的面向公众（SOCIAL）公告");
    ErrorCode NOTICE_DEADLINE_NOT_FOUND = new ErrorCode(1_060_101_008, "标段未找到对应的已发布公告截止时间");
    // ========== 备案（filing，BID-ENT-REG-01 / BID-SUP-FILE-01）==========
    // 注：备案语义上属 REG 域，但冻结规范 4.7 段位表暂无 REG 段（106~198 预留），
    // 且备案由公告与文件流程发起，故暂挂 101（公告与文件）；待规范登记 REG 段后迁移。
    ErrorCode FILING_ALREADY_SUBMITTED = new ErrorCode(1_060_101_010, "该备案已提交（幂等键=备案类型+项目编号）");
    ErrorCode FILING_NOT_EXISTS = new ErrorCode(1_060_101_011, "备案记录不存在");
    ErrorCode FILING_STATUS_INVALID = new ErrorCode(1_060_101_012, "备案状态不允许该操作（仅 PENDING 可回推终态）");

    // ========== 开标/评标 1-060-102-000 ==========
    ErrorCode BID_OPEN_RECORD_NOT_EXISTS = new ErrorCode(1_060_102_000, "开标记录不存在");
    ErrorCode EVAL_COMMITTEE_NOT_EXISTS = new ErrorCode(1_060_102_001, "评标委员会不存在");

    // ========== 中标/合同 1-060-103-000 ==========
    ErrorCode WIN_CANDIDATE_NOT_EXISTS = new ErrorCode(1_060_103_000, "中标候选人不存在");
    ErrorCode CONTRACT_NOT_EXISTS = new ErrorCode(1_060_103_001, "合同不存在");

    // ========== 主体/专家 1-060-104-000 ==========
    ErrorCode SUBJECT_NOT_EXISTS = new ErrorCode(1_060_104_000, "主体不存在");
    ErrorCode EXPERT_NOT_EXISTS = new ErrorCode(1_060_104_001, "专家不存在");

    // ========== 监管/信用 1-060-105-000 ==========
    ErrorCode COMPLAINT_NOT_EXISTS = new ErrorCode(1_060_105_000, "投诉不存在");
    ErrorCode ILLEGAL_INFO_NOT_EXISTS = new ErrorCode(1_060_105_001, "违法违规信息不存在");
    ErrorCode BLACKLIST_NOT_EXISTS = new ErrorCode(1_060_105_002, "黑名单信息不存在");

    // ========== 通用 1-060-199-000 ==========
    ErrorCode EXCHANGE_VO_VALIDATE_FAILED = new ErrorCode(1_060_199_000, "交换层VO校验失败");
    ErrorCode PUBLIC_LEVEL_FILTER_FAILED = new ErrorCode(1_060_199_001, "公开级别字段裁剪失败");

    // ========== 外部公告/采集/关注提醒 1-060-106-000（设计文档 §十五.2 已登记）==========
    ErrorCode PDF_EXTRACT_EMPTY = new ErrorCode(1_060_106_000, "PDF 抽取结果为空（扫描件或无可提取文本/表格），直接判失败");
    ErrorCode EXTERNAL_NOTICE_NOT_EXISTS = new ErrorCode(1_060_106_001, "外部公告不存在");
    ErrorCode EXTERNAL_NOTICE_TAKEN_DOWN = new ErrorCode(1_060_106_002, "外部公告已下线或不可展示");
    ErrorCode EXTERNAL_NOTICE_INGEST_INVALID = new ErrorCode(1_060_106_011, "采集产出落库数据不完整（标题/原网址必填）");
    ErrorCode EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID = new ErrorCode(1_060_106_013, "采集内容校验失败（URL 协议不支持/JSON 非法/超长），拒绝入库");
    ErrorCode EXTERNAL_NOTICE_STATE_INVALID = new ErrorCode(1_060_106_012, "外部公告当前状态不允许该操作");
    ErrorCode EXTERNAL_REDIRECT_FORBIDDEN = new ErrorCode(1_060_106_014, "跳转被拒绝：仅允许白名单采集源域名下的 http(s) 链接");
    ErrorCode EXTERNAL_NOTICE_SEARCH_UNAVAILABLE = new ErrorCode(1_060_106_015, "外部公告关键词检索不可用：请启用检索组件（bidding.es.enabled=true）");
    ErrorCode EXTERNAL_NOTICE_AI_NOT_CONFIGURED = new ErrorCode(1_060_106_016, "AI 结构化抽取未配置：需 bidding.ai.enabled=true 且 AI 渠道/服务就绪");
    ErrorCode EXTERNAL_NOTICE_AI_OUTPUT_INVALID = new ErrorCode(1_060_106_017, "AI 结构化输出非法（非 JSON 或空），已标记失败，可重试");
    ErrorCode EXTERNAL_NOTICE_AI_FAILED = new ErrorCode(1_060_106_018, "AI 结构化抽取失败（渠道/服务异常），已标记失败，可重试");
    ErrorCode EXTERNAL_NOTICE_AI_NO_MATERIAL = new ErrorCode(1_060_106_019, "AI 抽取无可用素材（本地无抽取文本；扫描件需先接入 PDF 附件访问）");
    ErrorCode EXTERNAL_NOTICE_BODY_STRUCTURE_FAILED = new ErrorCode(1_060_106_020, "正文结构化失败（AI 兜底排版异常或输出非法），可重试");
    ErrorCode CRAWL_SOURCE_NOT_EXISTS = new ErrorCode(1_060_106_010, "采集源不存在或未启用");

    // ========== 外部公告/采集/关注提醒 追加段（前端配合说明 N1/N2 + 爬虫管理面，106 段内顺延登记）==========
    ErrorCode CRAWL_SOURCE_CODE_DUPLICATE = new ErrorCode(1_060_106_021, "采集源代码已存在");
    ErrorCode CRAWL_SOURCE_IN_USE = new ErrorCode(1_060_106_022, "采集源使用中：存在未终态采集任务或展示中外部公告，不能删除，请先停用");
    ErrorCode CRAWL_TASK_NOT_EXISTS = new ErrorCode(1_060_106_023, "采集任务不存在");
    ErrorCode CRAWL_JOB_NOT_EXISTS = new ErrorCode(1_060_106_024, "采集批次不存在");
    ErrorCode CRAWL_TASK_NOT_RETRYABLE = new ErrorCode(1_060_106_025, "仅 FAILED（重试耗尽/死信）任务可人工重试");
    ErrorCode FOLLOW_TARGET_INVALID = new ErrorCode(1_060_106_026, "关注目标类型不支持：ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION");
    ErrorCode CRAWL_SOURCE_PAYLOAD_INVALID = new ErrorCode(1_060_106_027, "采集源配置校验失败（URL 协议不支持/通道未注册/入口 JSON 非法）");
    ErrorCode CRAWL_SOURCE_CODE_IMMUTABLE = new ErrorCode(1_060_106_028, "采集源代码不可修改（唯一身份；如需换码请删除后重建）");

    // ========== 主体信息/授权/投标资格门（106 段内追加；法规依据见 docs/法规依据/00-索引与引用说明.md）==========
    // [依据] 投标资格门：《招标投标法》第25/26条（投标人资格）；《实施条例》第19条（未通过资格预审的申请人不具有投标资格——平台侧以
    //       “已授权 + 主体信息完善”作为可投标前提）；《电子招标投标办法》第9/13/14/24条（免费注册登录、身份识别、初始录入信息真实性验证、
    //       投标人注册登记并如实递交信息）。招标人发布门：《招标投标法》第8/16条 + 《招标公告和公示信息发布管理办法》第5/6条（发布主体为招标人）。
    ErrorCode SUBJECT_TYPE_INVALID = new ErrorCode(1_060_106_030, "主体类型不支持（仅 BIDDER 投标人/TENDERER 招标人可授权）");
    ErrorCode SUBJECT_NOT_AUTHORIZED = new ErrorCode(1_060_106_031, "主体未经平台授权或授权已停用，无法执行该操作");
    ErrorCode SUBJECT_ALREADY_AUTHORIZED = new ErrorCode(1_060_106_032, "该企业主体已授权，请勿重复授权");
    ErrorCode SUBJECT_AUTH_NOT_EXISTS = new ErrorCode(1_060_106_033, "授权记录不存在");
    ErrorCode SUBJECT_PROFILE_INCOMPLETE = new ErrorCode(1_060_106_034, "主体信息未完善：投标人完善后方可在本项目投标；招标人完善后方可发布项目公告");
    ErrorCode SUBJECT_CREDIT_CODE_INVALID = new ErrorCode(1_060_106_035, "统一社会信用代码校验失败（须符合 GB 32100-2015，18 位含校验码）");
    ErrorCode SUBJECT_USER_NOT_BELONG_TENANT = new ErrorCode(1_060_106_036, "经办用户不属于该主体所属企业租户，无法绑定");
    ErrorCode SUBJECT_USER_ALREADY_BOUND = new ErrorCode(1_060_106_037, "该用户已绑定为该主体经办用户");
    ErrorCode SUBJECT_USER_NOT_EXISTS = new ErrorCode(1_060_106_038, "经办用户绑定记录不存在");
    ErrorCode SUBJECT_PAYLOAD_INVALID = new ErrorCode(1_060_106_039, "主体信息校验失败（行政区划代码/法人机构类别等格式不合法）");
    ErrorCode SUBJECT_USER_INVALID = new ErrorCode(1_060_106_040, "经办用户不存在或已被禁用，无法绑定");

    // ========== 采集解析器插件（crawl parser SPI；106 段顺延 041~ 登记，2026-09 解析器测试功能）==========
    // [依据] 爬虫“下载器(channel 插件,占位)→解析器(parser 插件)→ingest 落库”管线分层；解析器插件可插拔，
    //       管理端测试接口先于真实下载器落地（先解析后下载的开发顺序）。
    ErrorCode CRAWL_PARSER_NOT_FOUND = new ErrorCode(1_060_106_041, "解析器不存在或未注册：{0}");
    ErrorCode CRAWL_PARSER_PAYLOAD_MISSING = new ErrorCode(1_060_106_042, "解析器测试内容为空（HTML/JSON 文本与上传文件不能同时为空）");
    ErrorCode CRAWL_PARSER_INPUT_TYPE_UNSUPPORTED = new ErrorCode(1_060_106_043, "解析器 {0} 不支持该输入类型 {1}");
    ErrorCode CRAWL_PARSER_FILE_INVALID = new ErrorCode(1_060_106_044, "测试文件无效（仅支持 .html/.htm/.json；读取或编码失败）");
    ErrorCode CRAWL_PARSER_EXECUTE_FAILED = new ErrorCode(1_060_106_045, "解析器执行异常：{0}");
    ErrorCode CRAWL_SOURCE_INTERVAL_TOO_FREQUENT = new ErrorCode(1_060_106_046, "爬取间隔不能小于 120 秒（≥2 分钟，防目标站封禁）");
}
