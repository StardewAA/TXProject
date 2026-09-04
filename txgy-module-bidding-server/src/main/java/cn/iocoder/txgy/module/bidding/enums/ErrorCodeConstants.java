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

    // ========== 资质证书 1-060-106-000 ==========
    ErrorCode CERTIFICATE_NOT_EXISTS = new ErrorCode(1_060_106_000, "证照不存在");
    ErrorCode CERTIFICATE_NO_DUPLICATE = new ErrorCode(1_060_106_001, "证照编号已存在");
    ErrorCode CERTIFICATE_DATA_MODIFIED = new ErrorCode(1_060_106_002, "数据已被他人修改，请刷新后重试");
    ErrorCode CERTIFICATE_STATUS_TRANSITION_INVALID = new ErrorCode(1_060_106_003, "证照当前状态不允许该流转");
    ErrorCode CERT_CHANGE_LOG_LINK_BROKEN = new ErrorCode(1_060_106_004, "证照变更日志哈希链断裂");
    ErrorCode CERT_FILE_NOT_EXISTS = new ErrorCode(1_060_106_005, "证照文件不存在");
    ErrorCode CERT_FILE_VIRUS_NOT_CLEAN = new ErrorCode(1_060_106_006, "证照文件未通过病毒扫描，禁止预览/下载");
    ErrorCode CERT_FILE_HASH_DUPLICATE = new ErrorCode(1_060_106_007, "同证照已存在相同哈希的文件，禁止重复上传");
    ErrorCode CERT_FILE_VERSION_CONFLICT = new ErrorCode(1_060_106_008, "文件版本已存在（并发冲突），请刷新后重试");
    ErrorCode CERT_FILE_PARAM_INVALID = new ErrorCode(1_060_106_009, "文件参数不合法");
    ErrorCode CERT_ALERT_RECORD_NOT_EXISTS = new ErrorCode(1_060_106_010, "预警记录不存在");
    ErrorCode CERT_ALERT_RECORD_MODIFIED = new ErrorCode(1_060_106_011, "预警记录已被他人修改，请刷新");
    ErrorCode CERT_VERIFY_IN_PROGRESS = new ErrorCode(1_060_106_012, "验真进行中，请稍后刷新");
    ErrorCode CERT_VERIFY_GATEWAY_UNAVAILABLE = new ErrorCode(1_060_106_013, "核验通道未就绪，请联系管理员开通");
    ErrorCode CERT_VERIFY_CHANNEL_INVALID = new ErrorCode(1_060_106_014, "验真渠道不合法");

    // ========== 通用 1-060-199-000 ==========
    ErrorCode EXCHANGE_VO_VALIDATE_FAILED = new ErrorCode(1_060_199_000, "交换层VO校验失败");
    ErrorCode PUBLIC_LEVEL_FILTER_FAILED = new ErrorCode(1_060_199_001, "公开级别字段裁剪失败");
}
