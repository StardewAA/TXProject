package cn.iocoder.txgy.module.crm.enums.customer;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 客户类别枚举
 *
 * 对应字典 {@link cn.iocoder.txgy.module.crm.enums.DictTypeConstants#CRM_CUSTOMER_CATEGORY}
 *
 * @author 防腐保温项目组
 */
@RequiredArgsConstructor
@Getter
public enum CrmCustomerTypeEnum {

    DOWNSTREAM(1, "下游客户"),
    UPSTREAM(2, "上游供应商"),
    ;

    /**
     * 类别
     */
    private final Integer type;
    /**
     * 名称
     */
    private final String name;

    public static CrmCustomerTypeEnum getByType(Integer type) {
        if (type == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> ObjUtil.equal(item.getType(), type))
                .findFirst().orElse(null);
    }

    public static String getNameByType(Integer type) {
        CrmCustomerTypeEnum typeEnum = getByType(type);
        return typeEnum == null ? null : typeEnum.getName();
    }

    /**
     * 是否为上游供应商
     *
     * @param type 客户类别
     * @return 是否上游供应商
     */
    public static boolean isUpstream(Integer type) {
        return ObjUtil.equal(UPSTREAM.getType(), type);
    }

}
