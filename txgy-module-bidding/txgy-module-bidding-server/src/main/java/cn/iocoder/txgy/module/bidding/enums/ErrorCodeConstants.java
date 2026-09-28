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

    // [补充登记 2026-09-13] 关键岗位任职/变更登记写入（与上方 100 段同段位）
    // [规范出处] 用户业务口径"同一项目关键岗位人员频繁变更的监控（防挂靠/管理失控）"；《电子资质七大模块技术方案.md》§1.1(e)/§1.3。
    // [业务目标] 把"岗位非法、日期倒退、重复登记同一人、并发序号冲突、并发改任期"挡在写库之前，
    //           并给出可直接指导业务人员修正的提示；变更序号唯一性最终由数据库 uk_post_seq 兜底。
    // [为什么单独登记"重复登记同一人"这一条] 同一人在同一岗位重复登记会让"变更次数"虚增，
    //           把续任/补录误判为频繁变更——统计口径污染比写错一条数据更难发现，故直接拒绝。
    // [为什么不在这里再登记一个"关键岗位 code 非法"] 同一条件（postCode 不在 KeyPostEnum 内）复用既有
    //           KEY_POST_INVALID = 1_060_107_028（多岗兼任校验已在用）。一个条件两个码会让前端被迫
    //           对同一根因分支两次，也会让"错误码即口径"的检索失效。
    // [2026-09-26 修复] 818eefe 错误码撞段清理时被连带删除，导致 SafetyPermitRiskServiceImpl#assignKeyPost
    //           与 SafetyPermitRiskServiceImplTest 引用悬空（无法编译）；按 20a2341 原文恢复。
    ErrorCode PROJECT_KEY_POST_CHANGE_DATE_INVALID = new ErrorCode(1_060_100_009, "任职开始日（{0}）不得早于该岗位当前在任开始日（{1}）");
    ErrorCode PROJECT_KEY_POST_SEQ_CONFLICT = new ErrorCode(1_060_100_010, "该岗位变更序号已被并发登记占用，请刷新后重试");
    ErrorCode PROJECT_KEY_POST_SAME_PERSON = new ErrorCode(1_060_100_011, "该人员已是本岗位在任人员，无需重复登记（重复登记会虚增变更次数）");
    ErrorCode PROJECT_KEY_POST_CONCURRENT_MODIFIED = new ErrorCode(1_060_100_012, "该岗位在任记录已被他人修改，请刷新后重试");

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

    // ========== 文件读出口（101 段「公告与文件」顺延；016~019 已被发售期/投标期占用，故用 020~）==========
    // [依据]《文件接口收敛方案-后端实施篇-2026-09-20》：上传走 /bidding/file/upload、取文件走 /bidding/file/download，
    //   前端不再依赖 /infra/file/get（该接口需 infra:file:query，业务账号没有 → 权限倒挂）。
    // [病毒口径]《招投标模块开发规范》§5 三-5「上传须过病毒扫描」；INFECTED 一律拒绝下发。
    ErrorCode FILE_NOT_REGISTERED = new ErrorCode(1_060_101_020, "文件不存在或未登记存储坐标（fileId={}）");
    ErrorCode FILE_VIRUS_NOT_CLEAN = new ErrorCode(1_060_101_021, "文件未通过病毒扫描，禁止预览/下载（virusStatus=INFECTED）");
    ErrorCode FILE_STORAGE_COORD_INVALID = new ErrorCode(1_060_101_022, "文件存储坐标解析失败（地址非法或非本平台文件服务）");
    ErrorCode FILE_CONTENT_READ_FAILED = new ErrorCode(1_060_101_023, "文件内容读取失败，请稍后重试（{}）");

    // ========== CA 签章文件体积（101 段顺延）==========
    // [依据] 契约 A7：入口显式校验体积。Spring max-file-size 超限抛 MaxUploadSizeExceededException，
    //   前端拿到的是"长时间无响应 / 框架级 500"，无法定位原因 —— 这里提前拦下并给可读错误码。
    ErrorCode CA_SEAL_FILE_TOO_LARGE = new ErrorCode(1_060_101_024, "签章文件不得超过 {}MB，请压缩后重试");
    // [契约 e2e P0-6] /bidding/ca/verify 传入非 hex 摘要时，BouncyCastle Hex.decode 抛 DecoderException
    //   → 框架兜底成裸 500，调用方只看到"系统异常"，无法定位是入参格式问题。
    //   在入口显式校验格式并给业务码（判定口径同 LocalCryptoUtils.isSm3Hex：64 位十六进制）。
    ErrorCode CA_DIGEST_HEX_INVALID = new ErrorCode(1_060_101_025, "摘要格式不合法：应为 {} 位十六进制（SM3 国密摘要，GB/T 32905）");
    // [契约 e2e P0-7] /bidding/ca/decrypt/qrcode：测试配置（sdk.enabled=false）下组件不返回二维码，
    //   此前抛裸 IllegalStateException → 500；改为业务码并说明"测试配置不可用"，避免误判为系统故障。
    ErrorCode CA_DECRYPT_QRCODE_UNAVAILABLE = new ErrorCode(1_060_101_026, "解密二维码生成失败：{}");
    // [契约 e2e P0-4/P0-5] 导入接口在未携带 multipart 文件时，Spring 抛 MultipartException
    //   → 裸 500；入口显式校验并给业务码（前端据此提示"请选择文件"而非"系统异常"）。
    ErrorCode IMPORT_FILE_REQUIRED = new ErrorCode(1_060_101_027, "导入文件不能为空（请以 multipart/form-data 上传，字段名 file）");

    // ========== 发售期/投标期发布拦截（§5.3 第1/2条；101 段顺延 016~）==========
    // [依据] 实施条例第16条（发售期≥5日）、招标投标法第24条（投标期≥20日），发布公告前强校验。
    ErrorCode NOTICE_ISSUANCE_PERIOD_TOO_SHORT = new ErrorCode(1_060_101_016, "招标文件发售期不足 5 日（{} → {}），不得发布（实施条例第16条）");
    ErrorCode NOTICE_BID_PERIOD_TOO_SHORT = new ErrorCode(1_060_101_017, "投标期不足 20 日（发售开始 {} → 递交截止 {}），不得发布（招标投标法第24条）");
    ErrorCode NOTICE_PUBLICITY_PERIOD_TOO_SHORT = new ErrorCode(1_060_101_018, "中标候选人公示期不足 3 日（{} → {}），不得发布（实施条例第54条）");
    ErrorCode NOTICE_CHANGE_DEADLINE_TOO_SHORT = new ErrorCode(1_060_101_019, "变更/澄清公告距截止时间不足 {} 日（发出 {} → 截止 {}），不得发布（实施条例第21条，应顺延截止时间）");

    // ========== 开标/评标 1-060-102-000 ==========
    ErrorCode BID_OPEN_RECORD_NOT_EXISTS = new ErrorCode(1_060_102_000, "开标记录不存在");
    ErrorCode EVAL_COMMITTEE_NOT_EXISTS = new ErrorCode(1_060_102_001, "评标委员会不存在");

    // ========== 中标/合同 1-060-103-000 ==========
    ErrorCode WIN_CANDIDATE_NOT_EXISTS = new ErrorCode(1_060_103_000, "中标候选人不存在");
    ErrorCode CONTRACT_NOT_EXISTS = new ErrorCode(1_060_103_001, "合同不存在");
    // [依据]《实施条例》第57条（履约保证金不超过合同价10%）、招标投标法第46条（中标通知书发出30日内签约）
    ErrorCode CONTRACT_PERFORMANCE_BOND_EXCEEDS_LIMIT = new ErrorCode(1_060_103_002, "履约保证金 {} 元超过合同价 {} 元的 10%（实施条例第57条）");
    ErrorCode CONTRACT_SIGN_PERIOD_EXCEEDED = new ErrorCode(1_060_103_003, "合同签订时间距中标通知书发出超过 30 日（招标投标法第46条）");
    // [依据]《电子招标投标办法》技术规范（存档备查 ≥15 年）：归档后只读锁定，日志只追加不覆盖（说明书 §11.3 第 5 条）
    ErrorCode CONTRACT_ARCHIVED_LOCKED = new ErrorCode(1_060_103_004, "合同已归档只读锁定，禁止修改（存档 ≥15 年，电子办法技术规范）：{}");
    ErrorCode CONTRACT_ARCHIVE_STATUS_INVALID = new ErrorCode(1_060_103_005, "合同归档状态不允许该操作：{}（仅 SIGNED 可归档）");

    // ========== 主体/专家 1-060-104-000 ==========
    ErrorCode SUBJECT_NOT_EXISTS = new ErrorCode(1_060_104_000, "主体不存在");
    ErrorCode EXPERT_NOT_EXISTS = new ErrorCode(1_060_104_001, "专家不存在");

    // ========== 所属企业（SubjectCompany，1-060-104-002 起） ==========
    ErrorCode SUBJECT_COMPANY_NOT_EXISTS = new ErrorCode(1_060_104_002, "所属企业不存在");
    ErrorCode SUBJECT_COMPANY_USCC_DUPLICATE = new ErrorCode(1_060_104_003, "统一社会信用代码已存在（同租户内唯一）");
    ErrorCode SUBJECT_COMPANY_SELF_DUPLICATE = new ErrorCode(1_060_104_004, "本企业（SELF）已存在，每个租户仅一条");
    ErrorCode SUBJECT_COMPANY_PARENT_INVALID = new ErrorCode(1_060_104_005, "上级企业无效（须同租户、不能指向自身、不能成环）");
    ErrorCode SUBJECT_COMPANY_NAME_DUPLICATE = new ErrorCode(1_060_104_006, "企业名称已存在（同租户内 USCC 为空时按名称判重）");
    ErrorCode SUBJECT_COMPANY_DATA_MODIFIED = new ErrorCode(1_060_104_007, "所属企业已被他人修改，请刷新后重试");

    // ========== 监管/信用 1-060-105-000 ==========
    ErrorCode COMPLAINT_NOT_EXISTS = new ErrorCode(1_060_105_000, "投诉不存在");
    ErrorCode ILLEGAL_INFO_NOT_EXISTS = new ErrorCode(1_060_105_001, "违法违规信息不存在");
    ErrorCode BLACKLIST_NOT_EXISTS = new ErrorCode(1_060_105_002, "黑名单信息不存在");

    // ========== 内部编号生成 1-060-108-000（2026-09-15 登记；规范 §4.7，108 段此前空闲）==========
    // [依据] 《电子招标投标系统技术规范 第1部分 交易平台》附录B.3.2 编号规则（序列号段 0-9、a-z 6 位组合）；
    //       内部编号（公告/备案回执/内部项目）由平台按规则生成，序列耗尽/获取失败属生成侧异常。
    // 2026-09-13 修正：原占用 1_060_108_000/001，与"规则配置"段**重复**（错误码门禁 ErrorCodeFormatTest 当场报出）。
    // 按段位约定：108 段归规则配置（其 000~009 已用），内部编号迁到此前空闲的 110 段。
    ErrorCode BIZ_NO_SEQ_EXCEEDED = new ErrorCode(1_060_110_000, "内部编号序列号已用尽（{}），请更换分桶或联系管理员");
    ErrorCode BIZ_NO_SEQ_FAILED = new ErrorCode(1_060_110_001, "内部编号生成失败（序列获取异常）");

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
    ErrorCode CERT_FILE_ALREADY_VOIDED = new ErrorCode(1_060_109_028, "文件版本已作废，无需重复作废");
    ErrorCode CERT_FILE_CORRECT_NO_FIELD = new ErrorCode(1_060_109_029, "更正请求未包含任何可更正字段（备注/加注件元数据）");
    // ========== 平台审查任务中心（契约《后端变更请求-平台审查任务中心-2026-09-20》）==========
    ErrorCode REVIEW_TASK_NOT_EXISTS = new ErrorCode(1_060_109_030, "审查任务不存在");
    ErrorCode REVIEW_TASK_STATUS_INVALID = new ErrorCode(1_060_109_031, "审查任务状态不允许该操作（当前：{}）");
    ErrorCode REVIEW_TASK_REASON_REQUIRED = new ErrorCode(1_060_109_032, "驳回原因必填（驳回是整改依据，须留痕）");
    ErrorCode REVIEW_TASK_MODIFIED = new ErrorCode(1_060_109_033, "审查任务已被他人处理，请刷新后重试");
    ErrorCode CERT_ALERT_RECORD_MODIFIED = new ErrorCode(1_060_109_011, "预警记录已被他人修改，请刷新");
    ErrorCode CERT_VERIFY_IN_PROGRESS = new ErrorCode(1_060_109_012, "验真进行中，请稍后刷新");
    ErrorCode CERT_VERIFY_GATEWAY_UNAVAILABLE = new ErrorCode(1_060_109_013, "核验通道未就绪，请联系管理员开通");
    ErrorCode CERT_VERIFY_CHANNEL_INVALID = new ErrorCode(1_060_109_014, "验真渠道不合法");
    ErrorCode QUALIFICATION_NOT_EXISTS = new ErrorCode(1_060_109_015, "资质主档不存在");
    ErrorCode QUALIFICATION_NO_DUPLICATE = new ErrorCode(1_060_109_016, "同序列/类别/等级的资质证书编号已存在");
    ErrorCode QUALIFICATION_QUERY_ENT_REQUIRED = new ErrorCode(1_060_109_027, "企业主体不能为空（资质按企业主体查询）");
    ErrorCode QUALIFICATION_STATUS_TRANSITION_INVALID = new ErrorCode(1_060_109_017, "资质状态流转非法");
    ErrorCode QUALIFICATION_DATA_MODIFIED = new ErrorCode(1_060_109_018, "资质主档已被他人修改，请刷新");
    // [编号迁移 2026-09-22] 原用 109_028，与 LETTER 段既有的 CERT_FILE_ALREADY_VOIDED(109_028) 撞号
    //   （见上方同段注释：028/029 早已被"证照文件"占用）。规约测试 ErrorCodeFormatTest 捕获，迁至空闲位 109_038。
    ErrorCode QUALIFICATION_PARAM_INVALID = new ErrorCode(1_060_109_038, "资质参数不合法（更新需传主键 id 与乐观锁 version）");
    ErrorCode QUAL_APPLY_NOT_EXISTS = new ErrorCode(1_060_109_019, "申报记录不存在");
    ErrorCode QUAL_APPLY_STATUS_INVALID = new ErrorCode(1_060_109_020, "申报状态流转非法");
    ErrorCode QUAL_APPLY_PARAM_INVALID = new ErrorCode(1_060_109_021, "申报参数不合法（当前版本支持延续 RENEW 申报）");
    ErrorCode QUAL_STANDARD_NOT_FOUND = new ErrorCode(1_060_109_022, "未找到适用资质标准（按序列/类别/等级与生效区间）");
    ErrorCode QUAL_STANDARD_VERSION_DUPLICATE = new ErrorCode(1_060_109_023, "同标准版本/适用范围已存在，请换版本号");
    ErrorCode SAFETY_PERMIT_NOT_EXISTS = new ErrorCode(1_060_109_024, "安全生产许可证不存在");
    ErrorCode SAFETY_PERMIT_NO_DUPLICATE = new ErrorCode(1_060_109_025, "安全生产许可证编号已存在");
    ErrorCode SAFETY_PERMIT_STATUS_INVALID = new ErrorCode(1_060_109_026, "安许状态流转非法");

    // ========== 安许风险条目工单（契约 Q18/Q19，2026-09-28）==========
    // [编号迁移 2026-09-20] 原用 109_027~029，与「证照文件」CERT_FILE_ALREADY_VOIDED(028)/CERT_FILE_CORRECT_NO_FIELD(029)
    //   及「资质」QUALIFICATION_QUERY_ENT_REQUIRED(027) 撞号（规约测试 ErrorCodeFormatTest 会失败），迁至 109 段空闲位 034~036。
    ErrorCode SAFETY_RISK_ITEM_NOT_EXISTS = new ErrorCode(1_060_109_034, "安许风险条目不存在");
    ErrorCode SAFETY_RISK_ITEM_DATA_MODIFIED = new ErrorCode(1_060_109_035, "安许风险条目已被他人修改，请刷新后重试");
    ErrorCode SAFETY_RISK_ITEM_STATUS_INVALID = new ErrorCode(1_060_109_036, "安许风险条目状态流转非法");

    // ========== 安许风险规则实体（契约 e2e P1 方案A，2026-10-02）==========
    ErrorCode SAFETY_RISK_RULE_NOT_EXISTS = new ErrorCode(1_060_109_039, "风险规则不存在");
    ErrorCode SAFETY_RISK_RULE_DATA_MODIFIED = new ErrorCode(1_060_109_040, "风险规则已被他人修改，请刷新后重试");

    // ========== 主体档案缺失（一租户一档，2026-09-21 实测补齐）==========
    // [问题] 资质/证照等按企业主体查询的端点，在「本租户尚未建主体档案」时推导不出 entId，
    //   原先统一回落 QUALIFICATION_QUERY_ENT_REQUIRED「企业主体不能为空（资质按企业主体查询）」，
    //   前端看到会以为是自己漏传参数，实际是租户没建档（实测租户 2019：profile 0 条 → 报该错）。
    // [业务目标] 把「参数没传」与「租户没建档」分开，后者给出可操作指引（去完善主体档案）。
    // [契约《门户投标个人用户提示与资格门》3.2 文案修正] 原文案让企业员工"自己去完善"——但员工无权维护企业档案，
    //   正确指向是"告知公司"。改为可操作指引（与 106_034 同口径）。
    ErrorCode SUBJECT_PROFILE_NOT_BUILT = new ErrorCode(1_060_109_037, "请联系贵公司对企业档案进行完善（本企业尚未建立主体档案，由公司管理员在「设置 · 主体档案」维护）");

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
    // [契约《门户投标个人用户提示与资格门》3.2 文案修正] 同上：明确"该找谁"。
    ErrorCode SUBJECT_PROFILE_INCOMPLETE = new ErrorCode(1_060_106_034, "请联系贵公司对企业档案进行完善：{}（企业档案由公司管理员在「设置 · 主体档案」维护）");
    ErrorCode SUBJECT_CREDIT_CODE_INVALID = new ErrorCode(1_060_106_035, "统一社会信用代码校验失败（须符合 GB 32100-2015，18 位含校验码）");
    ErrorCode SUBJECT_USER_NOT_BELONG_TENANT = new ErrorCode(1_060_106_036, "经办用户不属于该主体所属企业租户，无法绑定");
    ErrorCode SUBJECT_USER_ALREADY_BOUND = new ErrorCode(1_060_106_037, "该用户已绑定为该主体经办用户");
    ErrorCode SUBJECT_USER_NOT_EXISTS = new ErrorCode(1_060_106_038, "经办用户绑定记录不存在");
    ErrorCode SUBJECT_PAYLOAD_INVALID = new ErrorCode(1_060_106_039, "主体信息校验失败（行政区划代码/法人机构类别等格式不合法）");
    ErrorCode SUBJECT_USER_INVALID = new ErrorCode(1_060_106_040, "经办用户不存在或已被禁用，无法绑定");
    // [契约 e2e P3-1] regCapCurrency 此前复用泛化的 SUBJECT_PAYLOAD_INVALID，
    //   提示只说"行政区划代码/法人机构类别等格式不合法"，看不出是币种字段出错
    //   （实测：前端传 CNY 得到该泛化提示，无从定位）。本码明确字段与格式。
    ErrorCode SUBJECT_REG_CAP_CURRENCY_INVALID = new ErrorCode(1_060_106_047, "注册资本币种格式不合法：{}（应为 GB/T 12406 三位数字码，如 156=人民币；非 CNY 这类字母码）");
    ErrorCode SUBJECT_INDUSTRY_CODE_INVALID = new ErrorCode(1_060_106_048, "行业类别格式不合法：{}（应为一个大写字母 + 两位数字，如 A01）");
    // [契约《门户投标个人用户提示与资格门-2026-09-29》3.2] 个人用户注册在保留租户 0，不可能持有企业角色
    //   权限码 ⇒ 调门户投标入口必然 @PreAuthorize 403。但 403 语义是"权限未开通"，与"本功能未对个人用户开放"
    //   是两件事；且前端对租户越权 403 会清会话，表现为"明明已登录、点建档却要求登录"。
    //   故改为可区分的业务错误码（不经 403 通道）。
    ErrorCode BID_PORTAL_PERSONAL_NOT_OPEN = new ErrorCode(1_060_106_049, "本功能未对个人用户开放（投标为面向企业的能力，请使用企业账号登录后操作）");

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

    // ========== 员工证书批量续期/提醒/验真（五页契约 Q11/Q12/Q13，2026-09-28）==========
    ErrorCode PERSONNEL_CERT_NOT_BOUND = new ErrorCode(1_060_107_015, "员工证书未绑定证照台账，无法走证照域验真（先建档绑定）");
    ErrorCode PERSONNEL_CERT_RENEW_INVALID = new ErrorCode(1_060_107_016, "续期后有效期不能早于当前日期或原有效期");

    // [依据] 用户范围口径（2026-09-13 确认）：做到"证照全生命周期管理（中级）"——变更/注销/年检/到期提醒/版本管理，不做签发；
    //       《电子证件全流程管理_数据库设计.md》v3.3 表 3/5/6/7（四类流程的状态与回写口径）；
    //       《招投标模块开发规范》§4.5（状态跃迁走接口 + 乐观锁 CAS，非法流转抛错）。
    // [业务目标] 生命周期各环节的非法流转、缺少必要字段、重复发起都能给出可操作的提示，保证流程留痕真实可信。
    // [编号迁移 2026-09-20] 原用 107_015/016，与 PERSONNEL_CERT_NOT_BOUND/RENEW_INVALID 撞号（规约测试会失败），迁至 107 段空闲位 029/030。
    ErrorCode CERT_LIFECYCLE_NOT_EXISTS = new ErrorCode(1_060_107_029, "证件生命周期记录不存在");
    ErrorCode CERT_LIFECYCLE_STATUS_INVALID = new ErrorCode(1_060_107_030, "生命周期流程状态流转非法（待办→审核中→通过→生效）");
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

    // ========== 标书智能体（知识库/智能体/召回评测）1-060-120-000（原 110 段，2026-09-23 与远端「内部编号生成」撞段迁入，规范 §4.7） ==========
    // [依据]《招投标模块开发规范》§4.7（子功能段先登记再占用，原登记 110 段）；
    //        用户口径 2026-09-15（TENDER 必须绑标段；golden set 由 A 存储、指标负责人提供标注）。
    // [业务目标] 底座不可用、角色非法、字段缺失等情形都必须给出可操作提示，禁止"看起来成功"的空结果。
    ErrorCode BID_AGENT_MATERIAL_NOT_EXISTS = new ErrorCode(1_060_120_000, "材料不存在，请刷新后重试");
    ErrorCode BID_AGENT_DOC_TYPE_INVALID = new ErrorCode(1_060_120_001, "材料角色不支持：{}（仅支持 TENDER/FACT/TEMPLATE）");
    ErrorCode BID_AGENT_MATERIAL_SECTION_REQUIRED = new ErrorCode(1_060_120_002, "招标文件必须绑定标段：检索需要按标段隔离");
    ErrorCode BID_AGENT_MATERIAL_FILE_EMPTY = new ErrorCode(1_060_120_003, "缺少文件访问地址，无法取内容入库");
    ErrorCode BID_AGENT_PROFILE_NOT_EXISTS = new ErrorCode(1_060_120_006, "智能体配置不存在");
    ErrorCode BID_AGENT_PROFILE_READONLY = new ErrorCode(1_060_120_007, "智能体提示词已被锁定为只读，后台不可覆盖：{}");
    ErrorCode BID_AGENT_EVAL_CASE_NOT_EXISTS = new ErrorCode(1_060_120_008, "精准度测试用例不存在");
    ErrorCode BID_AGENT_EVAL_CASE_EMPTY = new ErrorCode(1_060_120_009, "没有启用的测试用例：请先在「精准度测试」里添加用例");
    ErrorCode BID_AGENT_PREVIEW_UNAVAILABLE = new ErrorCode(1_060_120_010, "试跑不可用：{}");
    ErrorCode BID_AGENT_SEGMENT_NOT_EXISTS = new ErrorCode(1_060_120_011, "切片不存在，请刷新后重试");
    ErrorCode BID_AGENT_SEGMENT_CONTENT_EMPTY = new ErrorCode(1_060_120_012, "切片内容不能为空");
    ErrorCode BID_AGENT_SEGMENT_MERGE_REQUIRED = new ErrorCode(1_060_120_013, "合并切片至少需要选择两条相邻切片");




    // ========== 标书智能体运行时契约（项目/事实/检索/草稿/任务影子）1-060-121-000（原 111 段，2026-09-23 与远端「招标全流程」撞段迁入，规范 §4.7） ==========
    // [依据]《招投标模块开发规范》§4.7（子功能段先登记再占用，原登记 111 段）；
    //        并入来源 txgy-module-bid 原用 1-030-xxx-xxx（属其他模块段位），迁入后必须按本模块 060 段重编。
    // [业务目标] 契约端（/admin-api/bidding/bid-agent/contract/**）失败必须给出可分流的具体错误，
    //           禁止"看起来成功"的空结果；段内分块：000~009 项目、010~019 事实、020~029 检索、
    //           040~049 草稿、050~059 任务影子、060~069 文档解析与切片（原 030~039 docx 段随
    //           2026-09-22 移除 SDT docx 服务一并撤销，段位保留空置不另占用）。
    ErrorCode BID_PROJECT_NOT_EXISTS = new ErrorCode(1_060_121_000, "招投标项目不存在");
    ErrorCode BID_PROJECT_ALREADY_EXISTS = new ErrorCode(1_060_121_001, "招投标项目已存在");
    ErrorCode BID_FACT_NOT_EXISTS = new ErrorCode(1_060_121_010, "企业事实不存在");
    ErrorCode BID_RETRIEVE_QUERY_EMPTY = new ErrorCode(1_060_121_020, "检索词不能为空");
    ErrorCode BID_RETRIEVE_MANDATORY_MISS = new ErrorCode(1_060_121_021, "必检项未命中：{}");
    ErrorCode BID_RETRIEVE_VECTOR_UNAVAILABLE = new ErrorCode(1_060_121_022, "向量检索不可用，请检查 Qdrant 配置");
    ErrorCode BID_DRAFT_NOT_EXISTS = new ErrorCode(1_060_121_040, "章节草稿不存在");
    ErrorCode BID_TASK_SHADOW_NOT_EXISTS = new ErrorCode(1_060_121_050, "任务影子记录不存在");
    ErrorCode BID_DOC_EMPTY = new ErrorCode(1_060_121_060, "文档内容为空");
    ErrorCode BID_DOC_DOWNLOAD_FAIL = new ErrorCode(1_060_121_061, "文档下载失败");
    ErrorCode BID_DOC_READ_FAIL = new ErrorCode(1_060_121_062, "文档解析失败");
    ErrorCode BID_SEGMENT_SOURCE_EMPTY = new ErrorCode(1_060_121_063, "url 与 content 至少提供一个");
    // 070~079：检索链上云（2026-09-16）：本地 TEI 退役后，embedding/rerank 统一走阿里云百炼
    ErrorCode BID_AGENT_CLOUD_UNAVAILABLE = new ErrorCode(1_060_121_070, "云端检索不可用：{}");
    // 080~089：智能体平台（2026-09-16）：自有云端模型 + 技能 + MCP 服务
    ErrorCode BID_AGENT_LLM_UNAVAILABLE = new ErrorCode(1_060_121_080, "智能体模型不可用：{}");
    ErrorCode BID_AGENT_SKILL_NOT_EXISTS = new ErrorCode(1_060_121_081, "技能不存在");
    ErrorCode BID_AGENT_SKILL_CODE_DUPLICATE = new ErrorCode(1_060_121_082, "技能编码已存在：{}");
    ErrorCode BID_AGENT_MCP_NOT_EXISTS = new ErrorCode(1_060_121_083, "MCP 服务不存在");
    ErrorCode BID_AGENT_MCP_UNAVAILABLE = new ErrorCode(1_060_121_084, "MCP 服务不可用：{}");
    ErrorCode BID_AGENT_MODEL_NOT_EXISTS = new ErrorCode(1_060_121_085, "云端模型配置不存在");
    ErrorCode BID_AGENT_ORCHESTRATOR_UNAVAILABLE = new ErrorCode(1_060_121_086, "执行底座不可用：{}");
    // 090~099：一键生成标书任务
    ErrorCode BID_AGENT_TASK_NOT_EXISTS = new ErrorCode(1_060_121_090, "生成任务不存在");
    ErrorCode BID_AGENT_TASK_STATE_INVALID = new ErrorCode(1_060_121_091, "任务当前状态不允许该操作：{}");
    ErrorCode BID_AGENT_TASK_DUPLICATE = new ErrorCode(1_060_121_092, "该标段已有进行中的生成任务（任务号 {}），如需重跑请先取消或等待完成");
    ErrorCode BID_AGENT_RUNTIME_CONFIG_MISSING = new ErrorCode(1_060_121_093, "运行配置缺失：{}");
    ErrorCode BID_AGENT_TENDER_MATERIAL_MISSING = new ErrorCode(1_060_121_094, "缺少招标文件材料：请先在「知识库管理」上传该标段的招标文件（TENDER）");
    ErrorCode BID_AGENT_FILE_TYPE_INVALID = new ErrorCode(1_060_121_095, "文件类型不支持：{}（仅支持 pdf/doc/docx/txt/md/xlsx/pptx）");
    ErrorCode BID_AGENT_FILE_TOO_LARGE = new ErrorCode(1_060_121_096, "文件过大：{}（上限 60MB）");
    ErrorCode BID_AGENT_MODEL_NAME_DUPLICATE = new ErrorCode(1_060_121_097, "云端模型名称已存在：{}");
    ErrorCode BID_AGENT_PRICING_PARSE_FAILED = new ErrorCode(1_060_121_099, "报价 Excel 解析失败：{}");
    ErrorCode BID_AGENT_PRICING_NOT_EXISTS = new ErrorCode(1_060_121_100, "该项目暂无报价数据：请先在「知识库管理 → 资料管理」上传报价 Excel（角色 PRICING）");
    // ========== 智慧标书审查 1-060-113-000（方案《智慧标书审查-设计方案》V2.0 §10；规范 §4.7 段位表已登记）==========
    // [依据] 用户 2026-09-16 需求「前台智慧标书审查 - 管理端 + 个人端」；方案 V2.0（DeepSeek 单通道直连）。
    // [业务目标] 个人端一键生成失败时必须给"可操作"的提示，且 AI 相关失败要区分：
    //   未配置 / 密钥失效 / 返回格式异常 / 文件读不出字（扫描件）——四类处置方式完全不同，不能混成"系统异常"。
    // [段位迁移 2026-09-18] 原登记 110 段，合并 origin/bidding 时发现远端已把 110 段给了「内部编号生成」
    //   （BIZ_NO_SEQ_*，同码 1060110001 两义）；按规范 §4.7「搬走侵占方」处置——远端已发布故我方让位，
    //   整体迁至 113 段（113~198 为预留段，迁移时 113 空闲）。
    ErrorCode BID_REVIEW_TASK_NOT_EXISTS = new ErrorCode(1_060_113_001, "审查任务不存在");
    ErrorCode BID_REVIEW_TASK_NOT_OWNER = new ErrorCode(1_060_113_002, "只能查看本人的审查记录");
    ErrorCode BID_REVIEW_FILE_EMPTY = new ErrorCode(1_060_113_003, "请先上传投标文件（标书）");
    ErrorCode BID_REVIEW_PARSE_EMPTY = new ErrorCode(1_060_113_004, "文件无法解析出文字，扫描件请上传文字版 PDF 或 Word");
    ErrorCode BID_REVIEW_AI_DISABLED = new ErrorCode(1_060_113_005, "AI 审查未启用或未配置密钥，请联系管理员");
    ErrorCode BID_REVIEW_AI_PARSE_FAILED = new ErrorCode(1_060_113_006, "AI 返回格式异常，已降级为规则匹配");
    ErrorCode BID_REVIEW_TASK_RUNNING = new ErrorCode(1_060_113_007, "任务执行中，请稍后");
    ErrorCode BID_REVIEW_RETRY_LIMIT = new ErrorCode(1_060_113_008, "重试次数已达上限");
    ErrorCode BID_REVIEW_RULE_SET_NOT_EXISTS = new ErrorCode(1_060_113_009, "审查规则集不存在");
    ErrorCode BID_REVIEW_REPORT_NOT_READY = new ErrorCode(1_060_113_010, "报告尚未生成");
    ErrorCode BID_REVIEW_AI_KEY_INVALID = new ErrorCode(1_060_113_011, "AI 密钥无效或已过期，请联系管理员");
    ErrorCode BID_REVIEW_AI_CALL_FAILED = new ErrorCode(1_060_113_012, "无法连接 AI 服务（{}）");
    ErrorCode BID_REVIEW_RULE_SET_IN_USE = new ErrorCode(1_060_113_013, "规则集已被审查任务引用，不可删除");
    ErrorCode BID_REVIEW_ISSUE_NOT_EXISTS = new ErrorCode(1_060_113_014, "审查问题项不存在");
    ErrorCode BID_REVIEW_FILE_DOWNLOAD_FAILED = new ErrorCode(1_060_113_015, "标书文件下载失败（{}），请重新上传后再试");
    ErrorCode BID_REVIEW_ENTERPRISE_NOT_AUTHORIZED = new ErrorCode(1_060_113_016, "你尚未被该企业授权为经办人，无法以该企业名义提交");

    // ========== 招标全流程状态机 1-060-111-000（2026-09-20 登记；规范 §4.7，111 段此前空闲）==========
    // [依据] 用户裁定：招标全流程以状态机驱动（项目/招标项目/标段/公告生命周期），事件驱动、禁止业务代码直接 setStatus；
    //       政府审批为预留接口（当前 stub 直接返回通过，见 docs/前端规范-全域接口说明/27-政府审批接口预留-2026-09-20.md）。
    // 注：框架 ServiceExceptionUtil.doFormat 用 {} 占位符（勿用 {0}），见 txgy-framework ServiceExceptionUtil 注释
    ErrorCode FLOW_STATUS_TRANSITION_INVALID = new ErrorCode(1_060_111_000, "状态流转不允许（{}：{} → {}）");
    ErrorCode GOV_APPROVAL_BIZ_TYPE_INVALID = new ErrorCode(1_060_111_001, "审批对象类型不支持：{}");
    ErrorCode GOV_APPROVAL_BIZ_ID_REQUIRED = new ErrorCode(1_060_111_002, "审批对象业务标识（bizId）不能为空");
    ErrorCode GOV_APPROVAL_ACTION_INVALID = new ErrorCode(1_060_111_003, "审批动作不支持：{}（仅 APPROVE/REJECT）");
    ErrorCode FLOW_BIZ_TYPE_NOT_SUPPORTED = new ErrorCode(1_060_111_004, "流转对象类型不支持：{}（当前支持 PROJECT/TENDER_PROJECT/BID_SECTION；公告流转走既有公告服务）");
    ErrorCode FLOW_EVENT_INVALID = new ErrorCode(1_060_111_005, "流转事件不可执行：{}（{} 状态下不允许）");
    ErrorCode FLOW_ENTITY_NOT_FOUND = new ErrorCode(1_060_111_006, "流转对象不存在：{}/{}");
    ErrorCode FLOW_MANUAL_ADVANCE_DISABLED = new ErrorCode(1_060_111_007, "手动流转已关闭：事件 {} 由时间驱动（投标开始/截止、公示过期）触发，不支持手动执行（bidding.flow.manual-advance-enabled 可开启）");

    // ========== 招标文件下载名单（阶段四）1-060-114-000（2026-09-28 登记；112 段已归属投标流程跟踪，故迁 114）==========
    // [依据]《实施条例》第21条：澄清/修改应书面通知所有获取招标文件的潜在投标人——下载名单是通知的事实基础
    ErrorCode TENDER_DOC_DOWNLOAD_FILE_REQUIRED = new ErrorCode(1_060_114_000, "下载记录缺少文件ID（fileId）");

    // ========== 资格预审子域（阶段五）1-060-115-000（2026-09-28 登记；114 段已归属下载名单，故迁 115）==========
    // [依据]《实施条例》第16/17/19条：资格预审文件发售≥5日、申请期≥5日、未通过者无投标资格、通过者<3家应重新招标
    ErrorCode PREQUAL_NOT_EXISTS = new ErrorCode(1_060_115_000, "资格预审不存在：{}");
    ErrorCode PREQUAL_STATUS_INVALID = new ErrorCode(1_060_115_001, "资格预审状态不允许该操作：{}");
    ErrorCode PREQUAL_APPLY_PERIOD_TOO_SHORT = new ErrorCode(1_060_115_002, "申请期不足5日（自文件停止发售至申请截止，实施条例第17条）");
    ErrorCode PREQUAL_APPLY_DEADLINE_EXCEEDED = new ErrorCode(1_060_115_003, "资格预审申请已截止");
    ErrorCode PREQUAL_APPLY_ALREADY_EXISTS = new ErrorCode(1_060_115_004, "该主体已提交资格预审申请");
    ErrorCode PREQUAL_APPLY_NOT_EXISTS = new ErrorCode(1_060_115_005, "资格预审申请不存在：{}");
    ErrorCode PREQUAL_APPLY_STATUS_INVALID = new ErrorCode(1_060_115_006, "申请状态不允许该操作：{}");
    ErrorCode PREQUAL_ALREADY_RESULTED = new ErrorCode(1_060_115_007, "资格预审已出结果，不可修改");
    ErrorCode PREQUAL_COMMITTEE_REQUIRED = new ErrorCode(1_060_115_008, "国有资金占控股或主导地位的依法必招项目须组建资格审查委员会后方可审查（实施条例第18条）：{}");
    ErrorCode PREQUAL_COMMITTEE_INVALID = new ErrorCode(1_060_115_009, "资格审查委员会组成不合规：成员数须为 5 人以上单数且技术经济专家不少于 2/3（实施条例第37条），当前 {} 人");

    // ========== 投标保证金（阶段六）1-060-116-000（2026-09-28 登记；116 段此前预留）==========
    // [依据]《实施条例》第26条（投标保证金不超过估算价2%）、第57/58条（履约保证金/退还时限）
    ErrorCode BID_DEPOSIT_NOT_EXISTS = new ErrorCode(1_060_116_000, "投标保证金记录不存在：{}");
    ErrorCode BID_DEPOSIT_EXCEEDS_LIMIT = new ErrorCode(1_060_116_001, "投标保证金 {} 元超过估算价 {} 元的 2%（实施条例第26条）");
    ErrorCode BID_DEPOSIT_SECTION_NOT_FOUND = new ErrorCode(1_060_116_002, "标段不存在，无法登记保证金：{}");
    ErrorCode BID_DEPOSIT_ALREADY_REFUNDED = new ErrorCode(1_060_116_003, "投标保证金已退还，不可重复退还：{}");

    // ========== 定标书面报告（阶段九）1-060-117-000（2026-09-28 登记；117 段此前空闲）==========
    // [依据]《招标投标法》第47条：招标人应当自确定中标人之日起 15 日内向有关行政监督部门提交招标投标情况的书面报告
    ErrorCode REPORT_NOT_EXISTS = new ErrorCode(1_060_117_000, "定标书面报告不存在：{}");

    // ========== 异议（阶段五/六）1-060-118-000（2026-09-28 登记；118 段此前空闲）==========
    // [依据]《实施条例》第22条：资格预审文件异议在申请截止2日前提出、招标文件异议在投标截止10日前提出，招标人收到后3日内答复，答复前暂停
    ErrorCode OBJECTION_NOT_EXISTS = new ErrorCode(1_060_118_000, "异议记录不存在：{}");
    ErrorCode OBJECTION_PROPOSE_TOO_LATE = new ErrorCode(1_060_118_001, "异议提出已超期：资格预审文件异议须在申请截止 2 日前、招标文件异议须在投标截止 10 日前提出（实施条例第22条）");
    ErrorCode OBJECTION_ALREADY_REPLIED = new ErrorCode(1_060_118_002, "异议已答复，不可重复答复：{}");

    // ========== CA 签章台账 1-060-119-000（2026-09-28 登记；119 段此前空闲）==========
    // [依据]《电子招标投标办法》第20/30条（电子签名与签章）、《电子签名法》第13/14条（可靠电子签名）
    ErrorCode CA_SEAL_NOT_EXISTS = new ErrorCode(1_060_119_000, "CA 签章记录不存在：{}");
    // 契约「个人解密人须传身份证号 SM3 哈希」此前只写在文档里、代码不校验（传 01 不传 hash 也能出二维码），
    // 问题要到用户扫码才暴露且组件报错难懂；2026-09-21 实测补齐，在入口直接拦下。
    ErrorCode CA_DECRYPT_ID_CARD_HASH_REQUIRED = new ErrorCode(1_060_119_001, "个人解密人（decryptionUserType=01）必须传身份证号 SM3 哈希 idCardHash");
    // ========== 开标房间 1-060-102-000 顺延（最严格流程 2026-09-18；102=开标/评标段）==========
    // [依据] 电子招标投标办法(20号令)第30~33条（开标程序：投标人代表到场、当众解密、唱标）；
    //       用户裁定：开标视频不做，开标房间必须做好（房间成员列表 + 进房身份认证对接外部 CA）。
    ErrorCode OPENING_ROOM_NOT_EXISTS = new ErrorCode(1_060_102_002, "开标房间不存在（或开标记录未创建）");
    ErrorCode OPENING_ROOM_ALREADY_EXISTS = new ErrorCode(1_060_102_003, "该招标项目已存在开标房间（一条招标项目一条开标记录）");
    ErrorCode OPENING_ROOM_STATUS_INVALID = new ErrorCode(1_060_102_004, "开标房间当前状态不允许该操作（当前：{}）");
    ErrorCode OPENING_ROOM_CLOSED = new ErrorCode(1_060_102_005, "开标房间已关闭（开标结束），禁止进入");
    ErrorCode OPENING_MEMBER_NOT_EXISTS = new ErrorCode(1_060_102_006, "开标房间成员记录不存在");
    ErrorCode OPENING_MEMBER_ALREADY_IN = new ErrorCode(1_060_102_007, "该成员已在房间内（同一用户同一房间仅一条在线记录）");
    ErrorCode OPENING_MEMBER_AUTH_FAILED = new ErrorCode(1_060_102_008, "进房身份认证失败：{}（CA 证书无效/吊销/过期或身份不符）");
    ErrorCode OPENING_MEMBER_ROLE_INVALID = new ErrorCode(1_060_102_009, "进房身份不支持：{}（仅 TENDERER/PLATFORM/BIDDER/SUPERVISOR）");
    ErrorCode OPENING_OPEN_LIST_REQUIRED = new ErrorCode(1_060_102_010, "唱标清单不能为空（开标必须宣读全部投标人报价）");

    // ========== 投标流程跟踪 1-060-112-000（最严格流程 2026-09-18；112 段此前空闲）==========
    // [依据] 《投标流程跟踪功能设计方案》：意向投标→资质审核→待投标→已投标/流程结束；
    //       《电子招标投标办法》第24条（投标人应如实递交投标文件）、第28条（截止后不得递交/修改/撤换）。
    ErrorCode PROCESS_TRACK_NOT_EXISTS = new ErrorCode(1_060_112_000, "投标流程跟踪不存在");
    ErrorCode PROCESS_TRACK_ALREADY_EXISTS = new ErrorCode(1_060_112_001, "该标段已存在投标流程跟踪（每用户每标段一条，请勿重复建档）");
    ErrorCode PROCESS_TRACK_STATE_INVALID = new ErrorCode(1_060_112_002, "投标流程当前状态不允许该操作（当前：{}）");
    ErrorCode PROCESS_TRACK_BID_DEADLINE_PASSED = new ErrorCode(1_060_112_003, "投标已截止，不允许递交/修改投标文件（电子招标投标办法第28条）");
    ErrorCode PROCESS_TRACK_DOCUMENT_REQUIRED = new ErrorCode(1_060_112_004, "投标文件信息不完整（fileId/文件名/大小/SM3 指纹必填）");
    ErrorCode PROCESS_TRACK_CA_REQUIRED = new ErrorCode(1_060_112_005, "CA 证书信息必填（证书序列号 caCertNo 与证书公钥）");
    ErrorCode PROCESS_TRACK_SIGNATURE_REQUIRED = new ErrorCode(1_060_112_006, "投标文件数字签名必填（外部 CA SDK 签名后上送，SM2 base64）");
    // [契约 A9] 指纹算法只认国密 SM3（GB/T 32905）；证据链的算法标识必须可信，入口拒绝非法值。
    ErrorCode PROCESS_TRACK_HASH_ALGO_INVALID = new ErrorCode(1_060_112_007, "投标文件指纹算法不支持：{}（本平台采用国密 SM3，GB/T 32905）");
}
