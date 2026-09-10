package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ERP 采购申请 DO。 */
@TableName("erp_purchase_request")
@KeySequence("erp_purchase_request_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpPurchaseRequestDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private Integer status;
    private String processInstanceId;
    private LocalDateTime requestTime;
    private LocalDateTime requiredDate;
    private Long departmentId;
    private BigDecimal totalCount;
    private BigDecimal budgetAmount;
    private String purpose;
    private String fileUrl;
    private String remark;
}
