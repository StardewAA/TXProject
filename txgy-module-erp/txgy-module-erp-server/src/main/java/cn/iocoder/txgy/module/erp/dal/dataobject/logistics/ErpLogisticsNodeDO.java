package cn.iocoder.txgy.module.erp.dal.dataobject.logistics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("erp_logistics_node")
@KeySequence("erp_logistics_node_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpLogisticsNodeDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long relationId;
    private String requestId;
    private String requestHash;
    private String logisticsTaskNo;
    private String waybillNo;
    private String nodeCode;
    private String nodeName;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String locationDescription;
    private String remark;
    private String attachmentsJson;
    private String reportSource;
    private Long eventVersion;

}
