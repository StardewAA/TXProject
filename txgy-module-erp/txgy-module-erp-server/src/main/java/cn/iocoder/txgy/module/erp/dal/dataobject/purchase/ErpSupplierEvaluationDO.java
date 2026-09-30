package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ERP 供应商评价 DO。 */
@TableName("erp_supplier_evaluation")
@KeySequence("erp_supplier_evaluation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpSupplierEvaluationDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private Long supplierId;
    /**
     * 评价周期
     */
    private String evalPeriod;
    private LocalDateTime evalDate;
    /**
     * 质量得分
     */
    private BigDecimal qualityScore;
    /**
     * 交付得分
     */
    private BigDecimal deliveryScore;
    /**
     * 价格得分
     */
    private BigDecimal priceScore;
    /**
     * 服务得分
     */
    private BigDecimal serviceScore;
    /**
     * 综合得分
     */
    private BigDecimal totalScore;
    /**
     * 评价等级：A / B / C / D
     */
    private String grade;
    private Long evaluatorId;
    /**
     * 评价说明
     */
    private String comment;
    private Integer status;
    private String remark;
}
