package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ERP 询价报价 DO。 */
@TableName("erp_inquiry_quote")
@KeySequence("erp_inquiry_quote_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpInquiryQuoteDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private Long inquiryId;
    private Long supplierId;
    private Integer status;
    private Integer version;
    private LocalDateTime submittedAt;
    private LocalDateTime deliveryDate;
    private String paymentTerm;
    private BigDecimal totalPrice;
    private String fileUrl;
    private String remark;
}
