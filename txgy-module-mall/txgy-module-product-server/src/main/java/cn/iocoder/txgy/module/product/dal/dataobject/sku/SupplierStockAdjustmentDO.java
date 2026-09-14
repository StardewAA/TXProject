package cn.iocoder.txgy.module.product.dal.dataobject.sku;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/** 供应商本地可售库存调整记录；不表示采购入库或WMS凭证。 */
@TableName("product_supplier_stock_adjustment")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierStockAdjustmentDO extends TenantBaseDO {
    private Long id;
    private Long shopId;
    private Long spuId;
    private Long skuId;
    private Long memberId;
    private String requestKey;
    private Integer expectedStock;
    private Integer delta;
    private Integer beforeStock;
    private Integer afterStock;
    private String reason;
}
