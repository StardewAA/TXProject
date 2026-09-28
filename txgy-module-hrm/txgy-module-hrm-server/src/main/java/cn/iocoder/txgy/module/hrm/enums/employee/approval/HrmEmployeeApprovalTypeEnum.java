package cn.iocoder.txgy.module.hrm.enums.employee.approval;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * HRM 员工异动审批类型枚举
 *
 * <p>processDefinitionKey 与「工作流程 → 流程管理」里发布的流程标识一一对应。
 * 流程画好并发布后，只要 key 一致即可直接生效，<b>无需改代码</b>。</p>
 *
 * 推荐审批节点：
 * <ul>
 *     <li>转正：直属上级 → HR → 分管领导</li>
 *     <li>调岗/晋升：直属上级 → HR → 接收部门负责人 → 分管领导</li>
 *     <li>离职：直属上级 → HR → 财务（结算确认）→ 分管领导</li>
 * </ul>
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmEmployeeApprovalTypeEnum {

    REGULAR(1, "转正", "hrm_regular_approval", true),
    TRANSFER(2, "调岗/晋升", "hrm_transfer_approval", true),
    QUIT(3, "离职", "hrm_quit_approval", true);

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 名称
     */
    private final String name;
    /**
     * BPM 流程标识（与流程管理里发布的 key 一致）
     */
    private final String processDefinitionKey;
    /**
     * 是否支持审批通过后自动生效
     */
    private final Boolean autoApply;

    public static HrmEmployeeApprovalTypeEnum valueOf(Integer type) {
        for (HrmEmployeeApprovalTypeEnum item : values()) {
            if (item.getType().equals(type)) {
                return item;
            }
        }
        return null;
    }

}
