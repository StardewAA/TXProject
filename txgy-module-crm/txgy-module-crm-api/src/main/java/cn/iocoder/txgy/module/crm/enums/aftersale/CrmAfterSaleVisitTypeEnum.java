package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后回访方式
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleVisitTypeEnum implements ArrayValuable<Integer> {

    PHONE(1, "电话"),
    ON_SITE(2, "上门"),
    WECHAT(3, "微信"),
    EMAIL(4, "邮件");

    private final Integer type;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleVisitTypeEnum::getType).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
