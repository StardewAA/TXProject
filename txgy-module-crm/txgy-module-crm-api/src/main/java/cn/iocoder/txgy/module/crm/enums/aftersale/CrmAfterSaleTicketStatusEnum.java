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
    CLOSED(4, "已关闭");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleTicketStatusEnum::getStatus).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
