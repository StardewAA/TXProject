package cn.iocoder.txgy.module.bidding.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * bidding 的错误码。
 * 段位从 1-060-xxx-xxx 开始，按业务区块往下排，别跨段乱用。
 *
 * <p>规范出处：docs/招投标模块开发规范.md §4.7 错误码规范（1-060-xxx-xxx 结构，
 * 服务异常经 ServiceExceptionUtil 抛出，由框架全局异常处理器回写 HTTP 响应）；
 * 证书与资质台账子段 109（1-060-109-000~026，2026-09-13 由 106 段迁出——106 已登记给外部公告/采集域，
 * 两域原 000~026 重叠会造成同码两义；ErrorCodeFormatTest 门禁校验段位与格式）；
 * 员工资质域子段 107（1-060-107-000~021，人员档案/证书/配证/合规核验，2026-09-13 随员工资质模块登记）。
 */
public interface ErrorCodeConstants {

    // ========== 项目/招标项目/标段 1-060-100-000 ==========
    ErrorCode PROJECT_NOT_EXISTS = new ErrorCode(1_060_100_000, "项目不存在");
    ErrorCode TENDER_PROJECT_NOT_EXISTS = new ErrorCode(1_060_100_001, "招标项目不存在");
    ErrorCode BID_SECTION_NOT_EXISTS = new ErrorCode(1_060_100_002, "标段（包）不存在");
    ErrorCode BID_SECTION_CODE_DUPLICATE = new ErrorCode(1_060_100_003, "标段（包）编号已存在");
    // [补充登记 2026-09-10] 项目域唯一性与关联完整性（与上方 100 段同段位；依据数据规范 V2.0 唯一键口径 + 规范 §5）
    ErrorCode PROJECT_CODE_DUPLICATE = new ErrorCode(1_060_100_004, "项目编号已存在（租户内唯一，如需换号请删除后重建）");
    ErrorCode TENDER_PROJECT_CODE_DUPLICATE = new ErrorCode(1_060_100_005, "招标项目编号已存在（租户内唯一）");
    ErrorCode BID_SECTION_TENDER_PROJECT_REQUIRED = new ErrorCode(1_060_100_006, "标段必须归属已登记的招标项目（追溯链完整性）");
    ErrorCode PROJECT_HAS_SECTIONS = new ErrorCode(1_060_100_007, "项目下存在招标项目/标段，不能删除，请先清理下级");

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
    ErrorCode NOTICE_DELETE_STATUS_INVALID = new ErrorCode(1_060_101_009, "仅草稿/已撤下的公告可删除；已发布请先撤下，已归档为只读留痕禁删");
    // ========== 备案（filing，BID-ENT-REG-01 / BID-SUP-FILE-01）==========
    // 注：备案语义上属 REG 域，但冻结规范 4.7 段位表暂无 REG 段（106~198 预留），
    // 且备案由公告与文件流程发起，故暂挂 101（公告与文件）；待规范登记 REG 段后迁移。
    ErrorCode FILING_ALREADY_SUBMITTED = new ErrorCode(1_060_101_010, "该备案已提交（幂等键=备案类型+项目编号）");
    ErrorCode FILING_NOT_EXISTS = new ErrorCode(1_060_101_011, "备案记录不存在");
    ErrorCode FILING_STATUS_INVALID = new ErrorCode(1_060_101_012, "备案状态不允许该操作（仅 PENDING 可回推终态）");

    // ========== 文件注册（101 段「公告与文件」顺延 013~）==========
    // [规范依据]《招投标模块开发规范》§4.7 段位表（101=公告与文件）+ §5 三-5 文件存储口径
    //   （文件经 FileApi，库内只记 file_id + sha256 + size，URL 非一致性依据）+ 红线 8。
    // [业务目标] 业务表（notice.pdf_file_id / notice_external_attachment.file_id 等）以 file_id 为唯一关联键，
    //   故「上传成功但没有 file_id」必须作为**明确失败**返回，绝不能静默放行。
    // [为什么] 原平台上传接口 /infra/file/upload 的响应体是 URL 字符串（不含 id），前端取不到 file_id
    //   只能提示"需后端支持"；缺失 file_id 的表单若继续走发布流程，会在业务侧产生无法关联文件的脏数据。
    ErrorCode FILE_UPLOAD_EMPTY = new ErrorCode(1_060_101_013, "上传文件为空，请重新选择文件");
    ErrorCode FILE_REGISTER_FAILED = new ErrorCode(1_060_101_014, "文件注册失败，请重试（文件服务未返回 file_id）");
    ErrorCode FILE_URL_INVALID = new ErrorCode(1_060_101_015, "文件地址无效或不属于本平台文件服务，无法补登记 file_id");

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

    // ========== 证书与资质台账 1-060-109-000（规范 §4.7；2026-09-13 自 106 段迁出）==========
    // [依据] 规范 §4.7：106 段已登记给"外部公告/采集/关注提醒"，本块原占 106 段且未登记，
    //       与公告/采集块 000~026 整段重叠（同码两义，前端按码分流会误判），故整块迁至新登记的 109 段。
    // [业务目标] 错误码全局唯一可判定：证照不存在的 1090109000 与 PDF 抽取为空的 1060106000 不再混淆。
    ErrorCode CERTIFICATE_NOT_EXISTS = new ErrorCode(1_060_109_000, "证照不存在");
    ErrorCode CERTIFICATE_NO_DUPLICATE = new ErrorCode(1_060_109_001, "证照编号已存在");
    ErrorCode CERTIFICATE_DATA_MODIFIED = new ErrorCode(1_060_109_002, "数据已被他人修改，请刷新后重试");
    ErrorCode CERTIFICATE_STATUS_TRANSITION_INVALID = new ErrorCode(1_060_109_003, "证照当前状态不允许该流转");
    ErrorCode CERT_CHANGE_LOG_LINK_BROKEN = new ErrorCode(1_060_109_004, "证照变更日志哈希链断裂");
    ErrorCode CERT_FILE_NOT_EXISTS = new ErrorCode(1_060_109_005, "证照文件不存在");
    ErrorCode CERT_FILE_VIRUS_NOT_CLEAN = new ErrorCode(1_060_109_006, "证照文件未通过病毒扫描，禁止预览/下载");
    ErrorCode CERT_FILE_HASH_DUPLICATE = new ErrorCode(1_060_109_007, "同证照已存在相同哈希的文件，禁止重复上传");
    ErrorCode CERT_FILE_VERSION_CONFLICT = new ErrorCode(1_060_109_008, "文件版本已存在（并发冲突），请刷新后重试");
    ErrorCode CERT_FILE_PARAM_INVALID = new ErrorCode(1_060_109_009, "文件参数不合法");
    ErrorCode CERT_ALERT_RECORD_NOT_EXISTS = new ErrorCode(1_060_109_010, "预警记录不存在");
    ErrorCode CERT_ALERT_RECORD_MODIFIED = new ErrorCode(1_060_109_011, "预警记录已被他人修改，请刷新");
    ErrorCode CERT_VERIFY_IN_PROGRESS = new ErrorCode(1_060_109_012, "验真进行中，请稍后刷新");
    ErrorCode CERT_VERIFY_GATEWAY_UNAVAILABLE = new ErrorCode(1_060_109_013, "核验通道未就绪，请联系管理员开通");
    ErrorCode CERT_VERIFY_CHANNEL_INVALID = new ErrorCode(1_060_109_014, "验真渠道不合法");
    ErrorCode QUALIFICATION_NOT_EXISTS = new ErrorCode(1_060_109_015, "资质主档不存在");
    ErrorCode QUALIFICATION_NO_DUPLICATE = new ErrorCode(1_060_109_016, "同序列/类别/等级的资质证书编号已存在");
    ErrorCode QUALIFICATION_QUERY_ENT_REQUIRED = new ErrorCode(1_060_109_027, "企业主体不能为空（资质按企业主体查询）");
    ErrorCode QUALIFICATION_STATUS_TRANSITION_INVALID = new ErrorCode(1_060_109_017, "资质状态流转非法");
    ErrorCode QUALIFICATION_DATA_MODIFIED = new ErrorCode(1_060_109_018, "资质主档已被他人修改，请刷新");
    ErrorCode QUAL_APPLY_NOT_EXISTS = new ErrorCode(1_060_109_019, "申报记录不存在");
    ErrorCode QUAL_APPLY_STATUS_INVALID = new ErrorCode(1_060_109_020, "申报状态流转非法");
    ErrorCode QUAL_APPLY_PARAM_INVALID = new ErrorCode(1_060_109_021, "申报参数不合法（当前版本支持延续 RENEW 申报）");
    ErrorCode QUAL_STANDARD_NOT_FOUND = new ErrorCode(1_060_109_022, "未找到适用资质标准（按序列/类别/等级与生效区间）");
    ErrorCode QUAL_STANDARD_VERSION_DUPLICATE = new ErrorCode(1_060_109_023, "同标准版本/适用范围已存在，请换版本号");
    ErrorCode SAFETY_PERMIT_NOT_EXISTS = new ErrorCode(1_060_109_024, "安全生产许可证不存在");
    ErrorCode SAFETY_PERMIT_NO_DUPLICATE = new ErrorCode(1_060_109_025, "安全生产许可证编号已存在");
    ErrorCode SAFETY_PERMIT_STATUS_INVALID = new ErrorCode(1_060_109_026, "安许状态流转非法");

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


    // ========== 员工资质（personnel：人员档案/证书/培训/社保/劳动合同/智能配证/合规核验/企业材料）1-060-107-000 ==========
    // [依据] 用户业务口径《资质管理规范文件》三分类材料 + 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（人员配置台账：
    //       在职/社保/注册有效期=挂靠核查）与表 15-6（PASS/RISK/FAIL 三档 + diff 明细）；
    //       《招投标模块开发规范》§4.7——新段位先在规范 4.7 表登记，再登记进 ErrorCodeFormatTest 门禁集合（本段 107 已两处登记）。
    // [为什么] 人员域与企业资质域的错误语义不同（一人一档判重、配证人数不足、人证分离），单独段位便于前端按段归类提示。
    // [业务目标] 申报/投标前把"人证分离、证书失效、社保不一致"挡在提交之前，并保证错误提示可直接指导业务人员修正。
    ErrorCode PERSONNEL_NOT_EXISTS = new ErrorCode(1_060_107_000, "员工档案不存在");
    ErrorCode PERSONNEL_ID_CARD_DUPLICATE = new ErrorCode(1_060_107_001, "该身份证号已登记员工档案（一人一档，禁止重复录入）");
    ErrorCode PERSONNEL_STATUS_TRANSITION_INVALID = new ErrorCode(1_060_107_002, "员工在职状态流转非法");
    ErrorCode PERSONNEL_DATA_MODIFIED = new ErrorCode(1_060_107_003, "员工档案已被他人修改，请刷新后重试");
    ErrorCode PERSONNEL_NOT_ACTIVE = new ErrorCode(1_060_107_004, "员工非在职状态，不能参与投标配证/资质申报");
    ErrorCode PERSONNEL_CERT_NOT_EXISTS = new ErrorCode(1_060_107_005, "员工证书不存在");
    ErrorCode PERSONNEL_CERT_NO_DUPLICATE = new ErrorCode(1_060_107_006, "同员工同类型同编号证书已存在");
    ErrorCode PERSONNEL_CERT_STATUS_TRANSITION_INVALID = new ErrorCode(1_060_107_007, "员工证书状态流转非法");
    ErrorCode PERSONNEL_CERT_EXPIRED = new ErrorCode(1_060_107_008, "证书已失效（过期/吊销/注销），不能用于配证或申报");
    ErrorCode PERSONNEL_CERT_EXPIRY_REQUIRED = new ErrorCode(1_060_107_009, "该证书类型必须登记有效期（到期自动预警依赖此字段）");
    ErrorCode PERSONNEL_RECORD_NOT_EXISTS = new ErrorCode(1_060_107_010, "员工附属记录不存在（培训/社保/劳动合同）");
    ErrorCode PERSONNEL_BLIND_INDEX_REQUIRED = new ErrorCode(1_060_107_011, "身份证号（或其盲索引）必填：一人一档判重依赖此值");
    ErrorCode PERSONNEL_ALERT_SCAN_DISABLED = new ErrorCode(1_060_107_012, "员工证书到期预警未启用或阈值未配置");
    ErrorCode ENTERPRISE_MATERIAL_INCOMPLETE = new ErrorCode(1_060_107_013, "企业材料不完备，缺少：{0}");
    ErrorCode ENTERPRISE_MATERIAL_REQUIRED_EMPTY = new ErrorCode(1_060_107_014, "必备材料清单为空，无法执行完备性检查");

    // [依据] 用户范围口径（2026-09-13 确认）：做到"证照全生命周期管理（中级）"——变更/注销/年检/到期提醒/版本管理，不做签发；
    //       《电子证件全流程管理_数据库设计.md》v3.3 表 3/5/6/7（四类流程的状态与回写口径）；
    //       《招投标模块开发规范》§4.5（状态跃迁走接口 + 乐观锁 CAS，非法流转抛错）。
    // [业务目标] 生命周期各环节的非法流转、缺少必要字段、重复发起都能给出可操作的提示，保证流程留痕真实可信。
    ErrorCode CERT_LIFECYCLE_NOT_EXISTS = new ErrorCode(1_060_107_015, "证件生命周期记录不存在");
    ErrorCode CERT_LIFECYCLE_STATUS_INVALID = new ErrorCode(1_060_107_016, "生命周期流程状态流转非法（待办→审核中→通过→生效）");
    ErrorCode CERT_LIFECYCLE_DATA_MODIFIED = new ErrorCode(1_060_107_017, "生命周期记录已被他人修改，请刷新后重试");
    ErrorCode CERT_LIFECYCLE_CHANGE_EMPTY = new ErrorCode(1_060_107_018, "变更流程必须至少提交一项字段变更");
    ErrorCode CERT_LIFECYCLE_BIZ_DOMAIN_INVALID = new ErrorCode(1_060_107_019, "生命周期业务对象域不支持：{0}");
    ErrorCode CERT_LIFECYCLE_TARGET_REQUIRED = new ErrorCode(1_060_107_020, "该生命周期流程缺少必要字段：{0}");
    ErrorCode CERT_LIFECYCLE_APPLY_NO_DUPLICATE = new ErrorCode(1_060_107_021, "同对象同类型同申请单号的流程已存在（幂等防重复发起）");
    ErrorCode CERT_LIFECYCLE_BIZ_NOT_EXISTS = new ErrorCode(1_060_107_022, "生命周期关联的业务对象（证照/员工证书/资质/安许证）不存在");

    // [依据] 用户业务口径"员工信息一人一档、编号可检索"；《招投标模块开发规范》§5 六-1（业务键 UK 含 tenant_id 与 deleted）。
    // [业务目标] 同一企业内员工编号不可重复，重复录入即时给出可读提示（而不是抛数据库唯一键异常）。
    ErrorCode PERSONNEL_NO_DUPLICATE = new ErrorCode(1_060_107_023, "员工编号已存在：同一企业内员工编号不可重复");
    // [依据] 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（社保/劳动合同为挂靠核查证据，须按月/按份唯一）；
    //        《招投标模块开发规范》§4.5（幂等：同业务键重复提交直接拒绝）。
    // [业务目标] 同月社保、同编号合同不允许重复登记，避免月度核对时出现两条互相矛盾的记录导致结论不可复算。
    ErrorCode PERSONNEL_RECORD_DUPLICATE = new ErrorCode(1_060_107_024, "记录已存在：同一员工的该月份社保/该编号合同不可重复登记");

    // [依据] 用户业务口径（安许 A/B/C 联动、宽限规则可配、多岗兼任须持证）；《电子资质七大模块技术方案.md》§1.2~§1.4；
    //        《安全生产许可证条例》（国务院令第653号修正）。占位符用 hutool 风格 {}（见 108 段说明）。
    // [业务目标] 人数不达标、宽限被滥用、无证兼任等情形都能给出可操作提示，且提示里带出具体口径。
    // 注：安许"档案不存在"复用既有 1_060_109_024 SAFETY_PERMIT_NOT_EXISTS（109 段），不重复定义。
    ErrorCode SAFETY_GRACE_REASON_NOT_ALLOWED = new ErrorCode(1_060_107_025, "宽限原因不在允许范围内：{}（宽限仅限非主观过错情形）");
    ErrorCode SAFETY_GRACE_DAYS_EXCEEDED = new ErrorCode(1_060_107_026, "宽限天数超出配置上限：最多 {} 天");
    ErrorCode MULTI_POST_SAME_POST = new ErrorCode(1_060_107_027, "主岗位与兼任岗位不能相同");
    ErrorCode KEY_POST_INVALID = new ErrorCode(1_060_107_028, "关键岗位 code 不支持：{}");

    // ========== 规则配置中心与业务字典 1-060-108-000（方案 §8.1，契约 §1.1/§1.2）==========
    // [依据] 用户硬约束"所有数值参数必须支持动态配置，严禁硬编码"；《电子资质七大模块技术方案.md》§8.1/§8.2；
    //        《招投标模块开发规范》§4.7（新段位先登记规范表再登记进校验测试）、§4.5（乐观锁）。
    // [业务目标] 配置误配、越界、状态非法、并发覆盖都能给出可操作提示，保证"改配置即改行为"这一机制本身是安全的。
    // 注意：本平台 ServiceExceptionUtil.doFormat 使用 hutool 风格占位符 {}（按参数顺序替换），
    //      写成 {0}/{1} 不会被替换，用户看到的提示会残留占位符——契约 §1.2/§9 要求 msg 里必须带出
    //      具体下限天数与法规出处，故本段统一使用 {}。
    ErrorCode RULE_CONFIG_NOT_EXISTS = new ErrorCode(1_060_108_000, "规则配置不存在：{}");
    ErrorCode RULE_CONFIG_VALUE_TYPE_INVALID = new ErrorCode(1_060_108_001, "规则值类型不支持：{}");
    ErrorCode RULE_CONFIG_BOUND_VIOLATED = new ErrorCode(1_060_108_002, "配置值超出允许范围：{}（允许 {}）");
    ErrorCode RULE_CONFIG_STATUS_INVALID = new ErrorCode(1_060_108_003, "规则配置状态流转非法：{} → {}");
    ErrorCode RULE_CONFIG_CONSISTENCY_VIOLATED = new ErrorCode(1_060_108_004, "配置项之间关系不合法：{}");
    ErrorCode RULE_CONFIG_CHANGE_REASON_REQUIRED = new ErrorCode(1_060_108_005, "变更原因必填（配置影响线上判定，须留痕追责）");
    ErrorCode RULE_CONFIG_DATA_MODIFIED = new ErrorCode(1_060_108_006, "规则配置已被他人修改，请刷新后重试");
    ErrorCode RULE_CONFIG_VALUE_FORMAT_INVALID = new ErrorCode(1_060_108_007, "配置值与声明的值类型不匹配：{}");
    ErrorCode DICT_TYPE_NOT_SUPPORTED = new ErrorCode(1_060_108_008, "字典类型不支持：{}");
    ErrorCode LEGAL_FLOOR_VIOLATED = new ErrorCode(1_060_108_009, "预警天数不得低于法定下限 {} 天（依据：{}）");
}
