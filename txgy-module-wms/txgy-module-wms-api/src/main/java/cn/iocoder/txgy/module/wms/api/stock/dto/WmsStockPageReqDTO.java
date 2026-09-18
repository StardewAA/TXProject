package cn.iocoder.txgy.module.wms.api.stock.dto;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Read filters use WMS global codes, never ERP database IDs. */
@Data
@EqualsAndHashCode(callSuper = true)
public class WmsStockPageReqDTO extends PageParam {
    private String materialCode;
    private String materialName;
    private String skuCode;
    private String warehouseCode;
}

