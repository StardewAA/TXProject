package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/** ERP 到货检验单明细 DO。 */
@TableName("erp_arrival_inspection_item")
@KeySequence("erp_arrival_inspection_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpArrivalInspectionItemDO extends BaseDO {
    @TableId
    private Long id;
    private Long inspectionId;
    private Long productId;
    private Long productUnitId;
    /**
     * 到货数量
     */
    private BigDecimal deliveryCount;
    /**
     * 合格数量
     */
    private BigDecimal qualifiedCount;
    /**
     * 不合格数量
     */
    private BigDecimal unqualifiedCount;
    /**
     * 检验结论：1 合格，2 不合格
     */
    private Integer result;
    private String remark;
}
