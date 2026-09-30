package cn.iocoder.txgy.module.bidding.enums.prequal;

/**
 * 资格预审状态（招标方视角）。
 *
 * <p>设计依据：《电子招标投标全流程说明书》§6；《实施条例》第15/16/17/19条。
 * 生命周期：DRAFT→PUBLISHED→APPLYING→REVIEWING→RESULTED→CLOSED；
 * 终态：CLOSED；DRAFT 可 CLOSED（取消发起）。
 */
public enum PrequalStatusEnum {

    /** 草稿：编制中，可自由修改 */
    DRAFT("草稿"),
    /** 已发布：资格预审公告/文件已发布，开始接受申请 */
    PUBLISHED("已发布"),
    /** 申请中：接受资格预审申请（PUBLISHED 与 APPLYING 语义合并，申请期开始） */
    APPLYING("申请中"),
    /** 审查中：申请截止，资格审查委员会审查中 */
    REVIEWING("审查中"),
    /** 已出结果：审查结束，已发出结果通知书 */
    RESULTED("已出结果"),
    /** 已关闭：流程结束/取消（终态） */
    CLOSED("已关闭");

    private final String label;

    PrequalStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static PrequalStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (PrequalStatusEnum item : values()) {
            if (item.name().equals(code)) {
                return item;
            }
        }
        return null;
    }
}
