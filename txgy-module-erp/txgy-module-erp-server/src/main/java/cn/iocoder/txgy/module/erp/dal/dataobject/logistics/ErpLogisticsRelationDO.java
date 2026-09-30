package cn.iocoder.txgy.module.erp.dal.dataobject.logistics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("erp_logistics_relation")
@KeySequence("erp_logistics_relation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpLogisticsRelationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String requestId;
    private String requestHash;
    private String businessChainNo;
    private String logisticsTaskNo;
    private String waybillNo;
    private String sourceOrderType;
    private String sourceOrderNo;
    private String carrierCode;
    private String carrierName;
    private String vehicleNo;
    private String driverName;
    private String driverPhoneMasked;
    private LocalDateTime plannedDepartureTime;
    private LocalDateTime estimatedArrivalTime;
    private String logisticsStatus;
    private String resumeStatus;
    private Long latestEventVersion;
    private String latestNodeCode;
    private String latestNodeName;
    private LocalDateTime latestNodeTime;
    private String latestLocation;
    private String trackUrl;
    private Boolean locationOnline;
    private LocalDateTime lastOnlineTime;
    private Boolean hasException;

}
