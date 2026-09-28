package cn.iocoder.txgy.module.mes.enums.wm;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import cn.iocoder.txgy.module.mes.enums.MesOrderStatusConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * MES 销售出库单状态枚举
 *
 * 对应字典 mes_wm_product_sales_status
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum MesWmProductSalesStatusEnum implements ArrayValuable<Integer> {

    /**
     * 草稿
     *
     * 对应 MesWmProductSalesService#createProductSales 方法
     */
    PREPARE(MesOrderStatusConstants.PREPARE, "草稿"),
    /**
     * BPM 审批中
     *
     * 对应 MesWmProductSalesService#submitProductSales 方法：
     * 提交后发起 BPM 流程，审批通过后按 OQC 标志分流（待检测/待拣货），驳回退回草稿。
     * 6 为本枚举专属状态值（10 已被 SHIPPING 占用），不进 MesOrderStatusConstants
     */
    BPM_APPROVING(6, "BPM审批中"),
    /**
     * 待检测（需要 OQC 检验时），对接 qc 模块的 MesQcOqcDO 后续流程
     *
     * 对应 MesWmProductSalesService#submitProductSales 方法
     */
    CONFIRMED(MesOrderStatusConstants.CONFIRMED, "待检测"),
    /**
     * 待拣货
     *
     * 对应方法：
     * 1. 不需要 OQC 检验时：MesWmProductSalesService#submitProductSales 方法
     * 2. 或 OQC 检验完成时：MesWmProductSalesService#confirmProductSales 方法
     */
    APPROVING(MesOrderStatusConstants.APPROVING, "待拣货"),
    /**
     * 待填写运单
     *
     * 对应 MesWmProductSalesService#shippingProductSales 方法
     */
    SHIPPING(10, "待填写运单"), // 10 是一个特殊的状态值，不在 MesOrderStatusConstants 中，单独定义
    /**
     * 待执行出库
     *
     * 对应 MesWmProductSalesService#stockProductSales 方法
     */
    APPROVED(MesOrderStatusConstants.APPROVED, "待执行出库"),
    /**
     * 已完成
     *
     * 对应 MesWmProductSalesService#finishProductSales 方法
     */
    FINISHED(MesOrderStatusConstants.FINISHED, "已完成"),
    /**
     * 已取消
     *
     * 对应 MesWmProductSalesService#cancelProductSales 方法
     */
    CANCELED(MesOrderStatusConstants.CANCELLED, "已取消");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MesWmProductSalesStatusEnum::getStatus).toArray(Integer[]::new);

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
