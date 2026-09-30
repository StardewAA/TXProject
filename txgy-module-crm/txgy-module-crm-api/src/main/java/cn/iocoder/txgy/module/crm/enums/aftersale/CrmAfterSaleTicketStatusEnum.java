package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后问题工单状态
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleTicketStatusEnum implements ArrayValuable<Integer> {

    PENDING(1, "待处理"),
    PROCESSING(2, "处理中"),
    FINISH(3, "已完成"),
    CLOSED(4, "已关闭"),
    /**
     * 已超时：由系统超时检查任务自动标记 —— 开始处理时间（未开始则以创建时间兜底）
     * 加上售后设置「工单超时预警天数」已早于当前时间，且工单仍未闭环。
     * 标记后工单仍可开始处理、完成或关闭；恢复处理中或完成后即退出超时预警。
     */
    OVERDUE(5, "已超时");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleTicketStatusEnum::getStatus).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
