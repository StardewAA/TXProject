package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后质保状态
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleWarrantyStatusEnum implements ArrayValuable<Integer> {

    IN_WARRANTY(1, "质保中"),
    EXPIRING(2, "即将到期"),
    EXPIRED(3, "已到期终止");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleWarrantyStatusEnum::getStatus).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
