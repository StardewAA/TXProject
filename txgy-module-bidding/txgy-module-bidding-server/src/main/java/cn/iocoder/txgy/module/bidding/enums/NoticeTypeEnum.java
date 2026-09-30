package cn.iocoder.txgy.module.bidding.enums;
// 规范依据：《招投标模块开发规范》§2.1/2.2（全量VO、@PublicLevel）；权威数据集见 VO数据规范·公告公示类

import cn.iocoder.txgy.module.bidding.api.vo.TenderChangeNoticeVO;
import cn.iocoder.txgy.module.bidding.api.vo.TenderNoticeVO;
import cn.iocoder.txgy.module.bidding.api.vo.WinCandidatePublicityVO;
import cn.iocoder.txgy.module.bidding.api.vo.WinResultPublicityVO;

/**
 * 公告/公示类型（本模块内部登记码，对应权威数据集）。
 *
 * <p>类型 → 权威数据集 VO 一一对应：详情/新建的 payload 必须按对应数据集的字段全集
 * 规整（"该有的字段一个不能少"，见 {@code NoticePayloadUtils}），字段名照录规范英文名称。
 *
 * <p><b>本期支持范围</b>（已登记类型仅以下 4 个工程域数据集）：招标公告与资格预审公告、
 * 变更公告（工程）、中标候选人公示、中标结果公示。
 * 政府采购/土地/矿权/产权等其余"公告公示类"数据集（见 VO数据规范·公告公示类）未在本期登记，
 * 扩展方式：在本枚举登记新类型并给出对应 api/vo 数据集类 → 同步 NoticeTypeEnumTest/载荷用例。
 */
public enum NoticeTypeEnum {

    /** 招标公告与资格预审公告数据集 */
    TENDER_NOTICE("招标公告", TenderNoticeVO.class),
    /** 变更公告（工程） */
    CHANGE_NOTICE("变更公告", TenderChangeNoticeVO.class),
    /** 中标候选人公示数据集 */
    WIN_CANDIDATE_PUBLICITY("中标候选人公示", WinCandidatePublicityVO.class),
    /** 中标结果公示数据集 */
    WIN_RESULT_PUBLICITY("中标结果公示", WinResultPublicityVO.class);

    /** 展示名（内部页面用；对外报送名以数据集名称为准） */
    private final String label;
    /** 权威数据集 VO（api/vo，record，字段名照录规范） */
    private final Class<?> datasetClass;

    NoticeTypeEnum(String label, Class<?> datasetClass) {
        this.label = label;
        this.datasetClass = datasetClass;
    }

    public String getLabel() {
        return label;
    }

    public Class<?> getDatasetClass() {
        return datasetClass;
    }

    /** 按 code 查找；找不到返回 null（由调用方决定兜底或报错）。 */
    public static NoticeTypeEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (NoticeTypeEnum type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
