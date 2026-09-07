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
}
