package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/** ERP 采购申请明细 DO。 */
@TableName("erp_purchase_request_item")
@KeySequence("erp_purchase_request_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpPurchaseRequestItemDO extends BaseDO {
    @TableId
    private Long id;
    private Long requestId;
    private Long productId;
    private Long productUnitId;
    private BigDecimal count;
    private BigDecimal convertedCount;
    private BigDecimal targetPrice;
    private String remark;
}
