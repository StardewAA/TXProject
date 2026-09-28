package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后回访周期类型
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleVisitCycleEnum implements ArrayValuable<Integer> {

    QUARTER(1, "每季度"),
    HALF_YEAR(2, "每半年"),
    YEAR(3, "每年"),
    BEFORE_EXPIRE(4, "质保到期前重点回访"),
    MONTH(5, "每月");

    private final Integer type;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleVisitCycleEnum::getType).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
