package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 招标全流程运转模式（配置键 bidding.flow.mode）。
 *
 * <p>用户裁定：配置分为<b>测试中 / 实战</b>两种：
 * <ul>
 *   <li>TEST 测试中：招标方/平台方/政府审批方点击「下一步」或「审批通过」手动驱动状态流转（联调演示用）</li>
 *   <li>PROD 实战：靠时间推进（投标截止/公示期到期自动流转）+ 真实政府审批接口驱动</li>
 * </ul>
 */
public enum FlowModeEnum {

    /** 测试中：手动下一步 / 审批通过驱动流转 */
    TEST("测试中"),
    /** 实战：时间驱动 + 真实政府审批接口 */
    PROD("实战");

    private final String label;

    FlowModeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static FlowModeEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (FlowModeEnum mode : values()) {
            if (mode.name().equals(code)) {
                return mode;
            }
        }
        return null;
    }
}
