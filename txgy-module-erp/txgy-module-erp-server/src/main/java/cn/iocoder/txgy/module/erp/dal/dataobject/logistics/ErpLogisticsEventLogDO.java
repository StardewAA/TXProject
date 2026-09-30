package cn.iocoder.txgy.module.erp.dal.dataobject.logistics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("erp_logistics_event_log")
@KeySequence("erp_logistics_event_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpLogisticsEventLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long relationId;
    private String requestId;
    private String requestHash;
    private String logisticsTaskNo;
    private String eventType;
    private Long eventVersion;
    private String processStatus;
    private String processMessage;
    private String requestPayload;

}
