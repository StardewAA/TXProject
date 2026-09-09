package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * Item received from a mall shortage procurement demand.
 */
@TableName("erp_mall_purchase_demand_item")
@KeySequence("erp_mall_purchase_demand_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpMallPurchaseDemandItemDO extends BaseDO {

    @TableId
    private Long id;
    private Long demandId;
    private String demandItemId;
    private String skuId;
    private String erpMaterialCode;
    private String productName;
    private String specification;
    private String unitName;
    private BigDecimal orderQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal shortageQuantity;

}
