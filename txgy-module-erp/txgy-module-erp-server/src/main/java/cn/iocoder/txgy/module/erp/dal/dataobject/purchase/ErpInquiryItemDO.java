package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/** ERP 询价明细 DO。 */
@TableName("erp_inquiry_item")
@KeySequence("erp_inquiry_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpInquiryItemDO extends BaseDO {
    @TableId
    private Long id;
    private Long inquiryId;
    private Long purchaseRequestItemId;
    private Long productId;
    private Long productUnitId;
    private BigDecimal count;
    private String qualityRequirement;
    private String remark;
}
