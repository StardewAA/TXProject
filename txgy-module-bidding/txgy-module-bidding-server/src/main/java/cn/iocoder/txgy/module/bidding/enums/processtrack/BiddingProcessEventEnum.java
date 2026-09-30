package cn.iocoder.txgy.module.bidding.enums.processtrack;

/**
 * 投标流程跟踪事件（投标方视角状态机的动作清单）。
 */
public enum BiddingProcessEventEnum {

    /** 关注项目（触发：意向投标建档） */
    FOLLOW_PROJECT("关注项目"),
    /** 报名投标（意向投标→资质审核） */
    REGISTER("报名投标"),
    /** 审核通过（资质审核→待投标） */
    APPROVE("审核通过"),
    /** 审核不通过（资质审核→流程结束） */
    REJECT("审核不通过"),
    /** 上传投标文件（待投标→已投标；文件 CA 加密+SM3 指纹+SM2 签名由外部 CA SDK 完成） */
    UPLOAD_DOCUMENT("上传投标文件"),
    /** 取消投标（任意非终态→流程结束） */
    CANCEL("取消投标");

    private final String label;

    BiddingProcessEventEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按 code 反查枚举（兼容字典码 CREATE 与历史事件 FOLLOW_PROJECT：建档事件同一语义）。
     *
     * @param code 事件 code（CREATE/FOLLOW_PROJECT/REGISTER/APPROVE/REJECT/UPLOAD_DOCUMENT/CANCEL）
     * @return 枚举或 null
     */
    public static BiddingProcessEventEnum of(String code) {
        if (code == null) {
            return null;
        }
        if ("CREATE".equals(code)) {
            return FOLLOW_PROJECT;
        }
        for (BiddingProcessEventEnum item : values()) {
            if (item.name().equals(code)) {
                return item;
            }
        }
        return null;
    }

    /** 事件字典码（契约 §4.1 bidding_process_event：CREATE/REGISTER/APPROVE/REJECT/UPLOAD_DOCUMENT/CANCEL）。 */
    public String dictCode() {
        return this == FOLLOW_PROJECT ? "CREATE" : name();
    }
}
