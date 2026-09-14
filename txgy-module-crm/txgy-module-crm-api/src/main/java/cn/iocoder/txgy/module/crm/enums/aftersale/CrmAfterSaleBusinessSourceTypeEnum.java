package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后转商机内容方向
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleBusinessSourceTypeEnum implements ArrayValuable<Integer> {

    REPURCHASE(1, "复购"),
    RENEW(2, "续签"),
    NEW_PROJECT(3, "扩项/新工程");

    private final Integer type;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleBusinessSourceTypeEnum::getType).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
