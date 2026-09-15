package cn.iocoder.txgy.module.mes.enums.wm;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import cn.iocoder.txgy.module.mes.enums.MesOrderStatusConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * MES 供应商退货单状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum MesWmReturnVendorStatusEnum implements ArrayValuable<Integer> {

    /**
     * 草稿
     *
     * 对应 MesWmReturnVendorService#createReturnVendor 方法
     */
    PREPARE(MesOrderStatusConstants.PREPARE, "草稿"),
    /**
     * BPM 审批中
     *
     * 对应 MesWmReturnVendorService#submitReturnVendor 方法：
     * 提交后发起 BPM 流程，审批通过自动进入"待拣货"，驳回退回草稿
     */
    BPM_APPROVING(MesOrderStatusConstants.BPM_APPROVING, "BPM审批中"),
    /**
     * 待拣货
     *
     * 对应 MesWmReturnVendorService#submitReturnVendor 方法
     */
    APPROVING(MesOrderStatusConstants.APPROVING, "待拣货"),
    /**
     * 待执行退货
     *
     * 对应 MesWmReturnVendorService#stockReturnVendor 方法
     */
    APPROVED(MesOrderStatusConstants.APPROVED, "待执行退货"),
    /**
     * 已完成
     *
     * 对应 MesWmReturnVendorService#finishReturnVendor 方法
     */
    FINISHED(MesOrderStatusConstants.FINISHED, "已完成"),
    /**
     * 已取消
     *
     * 对应 MesWmReturnVendorService#cancelReturnVendor 方法
     */
    CANCELED(MesOrderStatusConstants.CANCELLED, "已取消");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MesWmReturnVendorStatusEnum::getStatus).toArray(Integer[]::new);

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
