package cn.iocoder.txgy.module.mes.enums.wms;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MES↔WMS 集成业务类型枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum MesWmsIntegrationOrderTypeEnum {

    /** 生产领料出库（WMS 单据类型 202，原料仓） */
    PRODUCTION_SHIPMENT("PRODUCTION_SHIPMENT", "生产领料出库"),
    /** 生产完工入库（WMS 单据类型 100，成品仓） */
    PRODUCTION_RECEIPT("PRODUCTION_RECEIPT", "生产完工入库");

    /** WMS 契约中的业务类型标识 */
    private final String code;
    /** 名称 */
    private final String name;

}
