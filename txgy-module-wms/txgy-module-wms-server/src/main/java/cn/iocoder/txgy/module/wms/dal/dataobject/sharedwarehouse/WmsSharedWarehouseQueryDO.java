package cn.iocoder.txgy.module.wms.dal.dataobject.sharedwarehouse;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 共享仓库聚合查询结果。
 */
@Data
public class WmsSharedWarehouseQueryDO {

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String sourceSystems;
    private Long wmsSkuCount;
    private Long mallSkuCount;
    private Long erpProductCount;
    private BigDecimal physicalQuantity;
    private String relatedErpWarehouseNames;
    private String remark;

}
