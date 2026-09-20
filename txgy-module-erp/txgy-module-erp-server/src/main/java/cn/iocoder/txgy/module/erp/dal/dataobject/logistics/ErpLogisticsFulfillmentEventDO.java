package cn.iocoder.txgy.module.erp.dal.dataobject.logistics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/** ERP 单据履约事件，独立于销售、退货等来源单据，避免影响现有业务表。 */
@TableName("erp_logistics_fulfillment_event")
@KeySequence("erp_logistics_fulfillment_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpLogisticsFulfillmentEventDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long relationId;
    private String eventCode;
    private String eventName;
    private String eventDescription;
    private LocalDateTime occurredAt;
    private Long eventVersion;

}
