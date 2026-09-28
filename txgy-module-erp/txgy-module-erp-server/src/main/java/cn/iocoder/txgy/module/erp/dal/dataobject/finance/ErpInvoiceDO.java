package cn.iocoder.txgy.module.erp.dal.dataobject.finance;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ERP 发票 DO。 */
@TableName("erp_invoice")
@KeySequence("erp_invoice_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpInvoiceDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    /**
     * 发票类型：1 增值税专用发票 2 增值税普通发票 3 电子发票
     */
    private Integer invoiceType;
    /**
     * 发票号码
     */
    private String invoiceNo;
    /**
     * 发票代码
     */
    private String invoiceCode;
    /**
     * 发票抬头
     */
    private String title;
    /**
     * 业务类型：1 采购进项 2 销售销项
     */
    private Integer bizType;
    private Long supplierId;
    private Long customerId;
    /**
     * 关联业务单号
     */
    private String orderNo;
    private LocalDateTime invoiceDate;
    /**
     * 价税合计
     */
    private BigDecimal amount;
    /**
     * 税额
     */
    private BigDecimal taxAmount;
    private BigDecimal taxRate;
    /**
     * 发票状态：0 待认证 1 已认证 2 已入账 3 已作废
     */
    private Integer status;
    /**
     * 认证时间
     */
    private LocalDateTime certifyTime;
    private String fileUrl;
    private String remark;
}
