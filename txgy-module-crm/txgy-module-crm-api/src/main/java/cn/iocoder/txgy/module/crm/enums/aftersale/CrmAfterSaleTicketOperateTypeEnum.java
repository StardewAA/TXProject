package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后工单流转操作类型（留痕）
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleTicketOperateTypeEnum implements ArrayValuable<Integer> {

    ASSIGN(1, "指派"),
    STATUS_CHANGE(2, "状态变更"),
    HANDLE(3, "处理记录"),
    CHARGEABLE(4, "转有偿"),
    CLOSE(5, "关闭");

    private final Integer type;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleTicketOperateTypeEnum::getType).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
