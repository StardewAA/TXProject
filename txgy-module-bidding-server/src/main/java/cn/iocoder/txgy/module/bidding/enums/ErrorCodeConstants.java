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
    ErrorCode CRAWL_SOURCE_NOT_EXISTS = new ErrorCode(1_060_106_010, "采集源不存在或未启用");
}
