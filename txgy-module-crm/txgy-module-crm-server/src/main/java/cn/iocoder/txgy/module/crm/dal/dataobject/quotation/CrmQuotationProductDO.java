package cn.iocoder.txgy.module.crm.dal.dataobject.quotation;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.product.CrmProductDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * CRM 报价单产品明细 DO
 *
 * @author 防腐保温项目组
 */
@TableName("crm_quotation_product")
@KeySequence("crm_quotation_product_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmQuotationProductDO extends BaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 报价编号
     *
     * 关联 {@link CrmQuotationDO#getId()}
     */
    private Long quotationId;
    /**
     * 产品编号
     *
     * 关联 {@link CrmProductDO#getId()}
     */
    private Long productId;
    /**
     * 产品单价，单位：元
     */
    private BigDecimal productPrice;
    /**
     * 报价价格，单位：元
     */
    private BigDecimal quotationPrice;
    /**
     * 数量
     */
    private BigDecimal count;
    /**
     * 总计价格，单位：元
     *
     * totalPrice = quotationPrice * count
     */
    private BigDecimal totalPrice;

}
