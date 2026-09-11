package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后回访计划状态
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleVisitPlanStatusEnum implements ArrayValuable<Integer> {

    ENABLE(1, "启用"),
    DISABLE(2, "停用"),
    FINISH(3, "已完成");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleVisitPlanStatusEnum::getStatus).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
