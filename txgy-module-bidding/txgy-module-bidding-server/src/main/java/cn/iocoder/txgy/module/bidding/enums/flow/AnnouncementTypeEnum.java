package cn.iocoder.txgy.module.bidding.enums.flow;

import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;

/**
 * 公告类型（规范版，招标全流程标准模型）。
 *
 * <p>五类公告贯穿全流程，每类公告的发布是招标项目/标段状态流转的<b>触发器</b>：
 * 发布时机会话见各枚举描述（对应 {@code TenderStatusEnum}/{@code BidSectionStatusEnum} 的流转）。
 *
 * <p>与既有 {@link NoticeTypeEnum}（4 个工程域数据集）映射：本枚举 TENDER_NOTICE/CHANGE_NOTICE 同名直接对应；
 * CANDIDATE_PUBLICITY↔WIN_CANDIDATE_PUBLICITY、RESULT_NOTICE↔WIN_RESULT_PUBLICITY；
 * TERMINATION_NOTICE 暂未登记工程域数据集（未 mapping，扩展时补 VO）。
 */
public enum AnnouncementTypeEnum {

    /** 招标公告：招标项目进入 ANNOUNCED */
    TENDER_NOTICE("招标公告", "招标项目进入 ANNOUNCED", NoticeTypeEnum.TENDER_NOTICE),
    /** 变更/澄清公告：投标截止前对招标文件的修改 */
    CHANGE_NOTICE("变更/澄清公告", "投标截止前对招标文件的修改", NoticeTypeEnum.CHANGE_NOTICE),
    /** 中标候选人公示：评标结束，招标项目进入 PUBLICIZING */
    CANDIDATE_PUBLICITY("中标候选人公示", "评标结束，进入 PUBLICIZING", NoticeTypeEnum.WIN_CANDIDATE_PUBLICITY),
    /** 中标结果公告：公示期满无异议，招标项目进入 AWARDED */
    RESULT_NOTICE("中标结果公告", "公示期满无异议，进入 AWARDED", NoticeTypeEnum.WIN_RESULT_PUBLICITY),
    /** 终止/流标公告：招标项目进入 TERMINATED 或标段 FAILED */
    TERMINATION_NOTICE("终止/流标公告", "进入 TERMINATED 或标段 FAILED", null);

    private final String label;
    private final String stage;
    private final NoticeTypeEnum noticeType;

    AnnouncementTypeEnum(String label, String stage, NoticeTypeEnum noticeType) {
        this.label = label;
        this.stage = stage;
        this.noticeType = noticeType;
    }

    public String getLabel() {
        return label;
    }

    public String getStage() {
        return stage;
    }

    /** 对应既有登记的数据集类型；TERMINATION_NOTICE 未登记返回 null。 */
    public NoticeTypeEnum getNoticeType() {
        return noticeType;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static AnnouncementTypeEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (AnnouncementTypeEnum type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
