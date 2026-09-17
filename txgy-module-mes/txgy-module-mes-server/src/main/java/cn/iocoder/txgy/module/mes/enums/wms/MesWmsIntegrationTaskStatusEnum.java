package cn.iocoder.txgy.module.mes.enums.wms;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MES↔WMS 集成任务状态枚举
 *
 * 状态机：0 待发送 → 1 已受理 → 2 执行中 → 3 成功；
 *        任意阶段可进入 4 失败（可重试到达上限或不可重试错误）/ 5 拒绝（WMS 审批拒绝）/ 6 取消
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum MesWmsIntegrationTaskStatusEnum {

    /** 待发送：尚未成功调用 WMS 申请接口 */
    PENDING_SEND(0, "待发送"),
    /** 已受理：WMS 已建单（草稿），等待仓库验收/审批 */
    ACCEPTED(1, "已受理"),
    /** 执行中：仓库审批通过，等待库存执行成功 */
    EXECUTING(2, "执行中"),
    /** 成功：库存、流水已提交 */
    SUCCESS(3, "成功"),
    /** 失败：发送失败（超上限/不可重试）或 WMS 出库执行失败 */
    FAILED(4, "失败"),
    /** 拒绝：WMS 仓库审批拒绝 */
    REJECTED(5, "拒绝"),
    /** 取消：WMS 流程或单据取消 */
    CANCELLED(6, "取消");

    private final Integer status;
    private final String name;

    /** 是否终态（终态后不再轮询，需人工重发/关闭） */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == REJECTED || this == CANCELLED;
    }

}
