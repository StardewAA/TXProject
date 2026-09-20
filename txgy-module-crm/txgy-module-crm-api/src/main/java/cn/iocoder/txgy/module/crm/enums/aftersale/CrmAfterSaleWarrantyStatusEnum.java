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

    /**
     * 按状态值获得状态名称
     *
     * @param status 状态值
     * @return 状态名称；未知状态返回 null
     */
    public static String getNameByStatus(Integer status) {
        for (CrmAfterSaleWarrantyStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item.name;
            }
        }
        return null;
    }

    /**
     * 是否已到期终止
     */
    public static boolean isExpired(Integer status) {
        return EXPIRED.status.equals(status);
    }

}
