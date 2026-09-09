package cn.iocoder.txgy.module.erp.dal.dataobject.integration;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("erp_wms_collaboration_b")
@KeySequence("erp_wms_collaboration_b_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpWmsCollaborationBDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String collaborationNo;
    private String businessChainNo;
    /** 协同单类型：PURCHASE_COLLABORATION/SALE_COLLABORATION/AWARD_COLLABORATION/SHORTAGE_COLLABORATION */
    private String collaborationType;
    private String sourceModule;
    private String sourceOrderNo;
    private String sourceEnterpriseCode;
    private String targetEnterpriseCode;
    private String currency;
    private String sourceProductionOrderNo;
    private String sourceCollaborationGNo;
    private String sourceSaleOrderNo;
    private String technicalStatus;
    private String businessStatus;
    private Long eventVersion;
    private LocalDateTime requiredAt;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private String reason;
    private Long confirmUserId;
    private String confirmUserName;
    private LocalDateTime confirmTime;
}
