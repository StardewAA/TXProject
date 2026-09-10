package cn.iocoder.txgy.module.erp.dal.dataobject.integration;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("erp_wms_integration_record")
@KeySequence("erp_wms_integration_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpWmsIntegrationRecordDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String requestId;
    private String direction;
    private String aggregateType;
    private String aggregateNo;
    private Long eventVersion;
    private String processingStatus;
    private String requestPayload;
    private String responsePayload;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;

}
