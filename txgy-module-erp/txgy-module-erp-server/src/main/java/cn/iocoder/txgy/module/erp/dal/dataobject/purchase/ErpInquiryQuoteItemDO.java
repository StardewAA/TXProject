package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/** ERP 询价报价明细 DO。 */
@TableName("erp_inquiry_quote_item")
@KeySequence("erp_inquiry_quote_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpInquiryQuoteItemDO extends BaseDO {
    @TableId
    private Long id;
    private Long quoteId;
    private Long inquiryItemId;
    private Long productId;
    private Long productUnitId;
    private BigDecimal count;
    private BigDecimal productPrice;
    private BigDecimal taxPercent;
    private BigDecimal taxPrice;
    private BigDecimal freightPrice;
    private BigDecimal totalPrice;
    private Boolean awarded;
    private BigDecimal awardCount;
    private String awardReason;
    private String remark;
}
