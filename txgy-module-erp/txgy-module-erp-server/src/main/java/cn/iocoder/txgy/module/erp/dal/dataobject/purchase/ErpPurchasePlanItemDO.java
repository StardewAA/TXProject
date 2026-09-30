package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ERP 采购计划明细 DO。 */
@TableName("erp_purchase_plan_item")
@KeySequence("erp_purchase_plan_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpPurchasePlanItemDO extends BaseDO {
    @TableId
    private Long id;
    /** 采购计划编号 */
    private Long planId;
    private Long productId;
    private Long productUnitId;
    /** 计划数量 */
    private BigDecimal count;
    /** 参考单价 */
    private BigDecimal targetPrice;
    /** 金额 */
    private BigDecimal amount;
    /** 需求日期 */
    private LocalDateTime requiredDate;
    private String remark;
}
