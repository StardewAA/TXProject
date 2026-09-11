package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ERP 询价单 DO。 */
@TableName("erp_inquiry")
@KeySequence("erp_inquiry_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpInquiryDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private Long purchaseRequestId;
    private Integer status;
    private String processInstanceId;
    private Integer businessStatus;
    private Integer awardStatus;
    private String awardProcessInstanceId;
    private String title;
    private Integer inquiryMode;
    private LocalDateTime deadline;
    private LocalDateTime deliveryDate;
    private String deliveryAddress;
    private String currency;
    private String paymentTerm;
    private String fileUrl;
    private String remark;
    private BigDecimal totalCount;
    private Integer quoteCount;
    private String purchaseOrderIds;
}
