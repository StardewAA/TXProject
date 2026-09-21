package cn.iocoder.txgy.module.wms.api.stock.dto;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** WMS read filters. Numeric identifiers are valid only inside the propagated tenant context. */
@Data
@EqualsAndHashCode(callSuper = true)
public class WmsStockPageReqDTO extends PageParam {
    private Long itemId;
    /** WMS SKU 主键。商城已建立正式映射时优先按主键精确查询。 */
    private Long skuId;
    /** WMS 仓库主键。 */
    private Long warehouseId;
    private String materialCode;
    private String materialName;
    private String skuCode;
    private String warehouseCode;
}

