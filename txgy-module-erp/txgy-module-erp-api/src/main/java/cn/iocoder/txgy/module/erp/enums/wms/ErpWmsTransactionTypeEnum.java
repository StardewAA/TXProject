package cn.iocoder.txgy.module.erp.enums.wms;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * WMS 库存事务类型枚举（方案A：WMS 作为实物唯一数据源）
 *
 * <p>WMS 每发生一笔实物异动，按本类型推送给 ERP，由 ERP 自动生成对应单据并记账。</p>
 *
 * @author OPENLAB BS
 */
@RequiredArgsConstructor
@Getter
public enum ErpWmsTransactionTypeEnum {

    INBOUND("INBOUND", "入库"),
    OUTBOUND("OUTBOUND", "出库"),
    /**
     * 盘点调整：明细数量为正表示盘盈（ERP 生成其它入库单），为负表示盘亏（ERP 生成其它出库单）
     */
    CHECK_ADJUST("CHECK_ADJUST", "盘点调整"),
    ;

    /**
     * 类型编码（WMS 推送时传的字符串）
     */
    private final String code;
    /**
     * 类型名称
     */
    private final String name;

    public static ErpWmsTransactionTypeEnum find(String code) {
        for (ErpWmsTransactionTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }

}
