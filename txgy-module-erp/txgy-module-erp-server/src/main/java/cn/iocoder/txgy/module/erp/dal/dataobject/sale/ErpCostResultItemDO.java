package cn.iocoder.txgy.module.erp.dal.dataobject.sale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/** ERP 造价结果明细 DO。 */
@TableName("erp_cost_result_item")
@KeySequence("erp_cost_result_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpCostResultItemDO extends BaseDO {
    @TableId
    private Long id;
    private Long costResultId;
    private Integer lineNo;
    private String externalMaterialCode;
    private String materialName;
    private String specification;
    private String unitName;
    private Long productId;
    private Long productUnitId;
    private Integer mappingStatus;
    private BigDecimal count;
    private BigDecimal unitPrice;
    private BigDecimal taxPercent;
    private BigDecimal totalPrice;
    private String remark;
}
