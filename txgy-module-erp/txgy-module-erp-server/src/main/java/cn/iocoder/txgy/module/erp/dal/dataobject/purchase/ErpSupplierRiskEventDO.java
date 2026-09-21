package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("erp_supplier_risk_event")
@KeySequence("erp_supplier_risk_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpSupplierRiskEventDO extends BaseDO {

    @TableId
    private Long id;
    private Long supplierId;
    private String eventType;
    private String status;
    private Integer severity;
    private Boolean blocksPurchase;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private String description;
    private String resolution;
}
