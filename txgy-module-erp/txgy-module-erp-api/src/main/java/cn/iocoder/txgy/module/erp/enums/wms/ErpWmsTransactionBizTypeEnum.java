package cn.iocoder.txgy.module.erp.enums.wms;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * WMS 库存事务的来源业务类型枚举（方案A）
 *
 * <p>用于说明这笔实物异动是「谁引起的」：MES 生产入库、商城出库、ERP 销售出库等。
 * ERP 侧会把它写进单据备注与库存流水，便于按来源追溯。</p>
 *
 * @author OPENLAB BS
 */
@RequiredArgsConstructor
@Getter
public enum ErpWmsTransactionBizTypeEnum {

    MES_PRODUCE_IN("MES_PRODUCE_IN", "MES 生产入库"),
    MALL_SALE_OUT("MALL_SALE_OUT", "商城销售出库"),
    ERP_SALE_OUT("ERP_SALE_OUT", "ERP 销售出库"),
    ERP_SALE_RETURN_IN("ERP_SALE_RETURN_IN", "ERP 销售退货入库"),
    PURCHASE_IN("PURCHASE_IN", "采购入库"),
    PURCHASE_RETURN_OUT("PURCHASE_RETURN_OUT", "采购退货出库"),
    CHECK_PROFIT("CHECK_PROFIT", "盘盈调整"),
    CHECK_LOSS("CHECK_LOSS", "盘亏调整"),
    OTHER("OTHER", "其它"),
    ;

    /**
     * 业务类型编码
     */
    private final String code;
    /**
     * 业务类型名称
     */
    private final String name;

    public static ErpWmsTransactionBizTypeEnum find(String code) {
        for (ErpWmsTransactionBizTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 取业务类型名称，识别不到时返回原编码
     */
    public static String nameOf(String code) {
        ErpWmsTransactionBizTypeEnum item = find(code);
        return item == null ? code : item.getName();
    }

}
