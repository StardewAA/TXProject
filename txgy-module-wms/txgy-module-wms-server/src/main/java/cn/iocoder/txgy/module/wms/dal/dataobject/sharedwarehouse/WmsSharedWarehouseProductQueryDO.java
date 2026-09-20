package cn.iocoder.txgy.module.wms.dal.dataobject.sharedwarehouse;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 共享仓库商品聚合查询结果。
 */
@Data
public class WmsSharedWarehouseProductQueryDO {

    private Long warehouseId;
    private String warehouseName;
    private Long wmsSkuId;
    private String wmsSkuName;
    private Long wmsItemId;
    private String wmsItemName;
    private String barCode;
    private BigDecimal physicalQuantity;
    private Long mallSkuId;
    private Long mallSpuId;
    private String mallSpuName;
    private Integer mallCachedStock;
    private Long erpProductId;
    private String erpProductName;

}
