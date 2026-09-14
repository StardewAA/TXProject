package cn.iocoder.txgy.module.bidding.enums;
// [依据] 平台授权管理：管理端“显示已授权投标人并支持管理”见需求（主体授权由平台侧核发，先授权、后完善、再参与）。
// 授权状态与业务门的关系在 SubjectEligibilityServiceImpl 内统一定义。

/**
 * 主体授权状态（平台管理端核发/停用/复用启用；撤销授权=置 DISABLED，保留留痕行）。
 */
public enum SubjectAuthStatusEnum {

    /** 已授权（可进入后续流程：投标人可完善档案后投标；招标人可完善后发布其项目公告） */
    ENABLED("已授权"),
    /** 已停用（平台撤销授权；停用即失去投标/发布资格，档案保留） */
    DISABLED("已停用");

    private final String label;

    SubjectAuthStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static SubjectAuthStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (SubjectAuthStatusEnum status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
