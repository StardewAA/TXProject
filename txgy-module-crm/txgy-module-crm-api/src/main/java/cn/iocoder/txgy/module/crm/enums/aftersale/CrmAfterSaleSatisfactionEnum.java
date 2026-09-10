package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后满意度评价
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleSatisfactionEnum implements ArrayValuable<Integer> {

    VERY_DISSATISFIED(1, "非常不满意"),
    DISSATISFIED(2, "不满意"),
    GENERAL(3, "一般"),
    SATISFIED(4, "满意"),
    VERY_SATISFIED(5, "非常满意");

    private final Integer level;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleSatisfactionEnum::getLevel).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
