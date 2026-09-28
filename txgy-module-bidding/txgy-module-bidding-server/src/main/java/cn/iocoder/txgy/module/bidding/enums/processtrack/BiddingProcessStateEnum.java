package cn.iocoder.txgy.module.bidding.enums.processtrack;

/**
 * 投标流程跟踪状态（投标方视角，T 轨）。
 * 设计依据：《投标流程跟踪功能设计方案》—— 意向投标 → 资质审核 → 待投标 → 已投标 / 流程结束。
 */
public enum BiddingProcessStateEnum {

    /** 意向投标（关注项目后自动进入） */
    INTENTION("意向投标"),
    /** 资质审核（报名后等待审核） */
    QUALIFICATION_REVIEW("资质审核"),
    /** 待投标（审核通过，准备标书） */
    PENDING_BID("待投标"),
    /** 已投标（已上传加密投标文件） */
    BIDDED("已投标"),
    /** 流程结束（审核不通过或取消） */
    TERMINATED("流程结束");

    private final String label;

    BiddingProcessStateEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static BiddingProcessStateEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (BiddingProcessStateEnum item : values()) {
            if (item.name().equals(code)) {
                return item;
            }
        }
        return null;
    }
}
