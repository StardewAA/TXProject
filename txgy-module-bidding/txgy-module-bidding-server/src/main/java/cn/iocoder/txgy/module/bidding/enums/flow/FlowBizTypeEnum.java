package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 招标全流程流转实体类型（状态流转服务的 bizType）。
 *
 * <p>当前流转落点：PROJECT/TENDER_PROJECT/BID_SECTION（三档主档 state 由流转服务统一推进）；
 * ANNOUNCEMENT 走既有公告服务（NoticeService.publish/unpublish，含发布门/索引/关注扇出），不在本服务直接改状态。
 */
public enum FlowBizTypeEnum {

    /** 项目（bidding_project.state，ProjectStatusEnum） */
    PROJECT("项目"),
    /** 招标项目（bidding_tender_project.state，TenderStatusEnum） */
    TENDER_PROJECT("招标项目"),
    /** 标段（bidding_bid_section.state，BidSectionStatusEnum） */
    BID_SECTION("标段"),
    /** 公告（bidding_notice.status，既有公告状态机；流转走公告服务，本服务不支持直接改） */
    ANNOUNCEMENT("公告");

    private final String label;

    FlowBizTypeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static FlowBizTypeEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (FlowBizTypeEnum type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
