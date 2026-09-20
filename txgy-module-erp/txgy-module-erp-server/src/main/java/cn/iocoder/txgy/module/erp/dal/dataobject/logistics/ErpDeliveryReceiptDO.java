package cn.iocoder.txgy.module.erp.dal.dataobject.logistics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("erp_delivery_receipt")
@KeySequence("erp_delivery_receipt_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpDeliveryReceiptDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long relationId;
    private String requestId;
    private String requestHash;
    private String logisticsTaskNo;
    private String sourceOrderNo;
    private String receiptType;
    private String receiverName;
    private LocalDateTime receiptTime;
    private BigDecimal receiptQuantity;
    private String remark;
    private String attachmentsJson;
    private Long eventVersion;

}
