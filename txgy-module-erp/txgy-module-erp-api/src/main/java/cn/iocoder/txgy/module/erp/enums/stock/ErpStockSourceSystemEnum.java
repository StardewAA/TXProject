package cn.iocoder.txgy.module.erp.enums.stock;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Objects;

/**
 * ERP 库存流水 - 来源系统枚举
 *
 * <p>用于区分库存变动的归属方。只有 {@link #ERP} 且业务类型为「销售出库」时，
 * 才算 ERP 自己的销售；商城发货、MES 领料、WMS 手工调整都不计入 ERP 销售统计。</p>
 *
 * @author OPENLAB BS
 */
@RequiredArgsConstructor
@Getter
public enum ErpStockSourceSystemEnum {

    ERP("ERP", "ERP 系统"),
    MALL("MALL", "商城"),
    MES("MES", "MES 系统"),
    MANUAL("MANUAL", "手工调整"),
    ;

    /**
     * 来源系统标识
     */
    private final String system;
    /**
     * 来源系统名称
     */
    private final String name;

    public static ErpStockSourceSystemEnum getBySystem(String system) {
        return Arrays.stream(values())
                .filter(item -> Objects.equals(item.system, system))
                .findFirst().orElse(null);
    }

    public static String getNameBySystem(String system) {
        ErpStockSourceSystemEnum item = getBySystem(system);
        return item != null ? item.getName() : system;
    }

}
