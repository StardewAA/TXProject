package cn.iocoder.txgy.module.mes.enums.pro;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * MES 生产工单状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum MesProWorkOrderStatusEnum implements ArrayValuable<Integer> {

    /**
     * 草稿
     *
     * 对应 MesProWorkOrderService#createWorkOrder 方法
     */
    PREPARE(0, "草稿"),
    /**
     * 已确认
     *
     * 对应 MesProWorkOrderService#confirmWorkOrder 方法
     */
    CONFIRMED(1, "已确认"),
    /**
     * 审批中
     *
     * 对应 MesProWorkOrderService#submitWorkOrder 方法
     */
    APPROVING(12, "审批中"),
    /**
     * 已批准
     *
     * 对应 MesProWorkOrderService#approveWorkOrder 方法（BPM 审批通过后置位，仅已批准工单可下发）
     */
    APPROVED(13, "已批准"),
    /**
     * 已完成
     *
     * 对应 MesProWorkOrderService#finishWorkOrder 方法
     */
    FINISHED(2, "已完成"),
    /**
     * 已取消
     *
     * 对应 MesProWorkOrderService#cancelWorkOrder 方法
     */
    CANCELED(3, "已取消"),
    /**
     * 已下发
     *
     * 对应 MesProWorkOrderService#issueWorkOrder 方法
     */
    ISSUED(10, "已下发"),
    /**
     * 生产中
     *
     * 首次报工自动置位，对应 MesProWorkOrderService#markInProductionIfIssued 方法
     */
    IN_PRODUCTION(11, "生产中");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MesProWorkOrderStatusEnum::getStatus)
            .toArray(Integer[]::new);

    /**
     * 状态值
     */
    private final Integer status;
    /**
     * 状态名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}