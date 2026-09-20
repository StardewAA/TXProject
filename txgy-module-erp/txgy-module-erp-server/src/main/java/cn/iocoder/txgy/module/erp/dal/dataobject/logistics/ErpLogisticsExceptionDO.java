package cn.iocoder.txgy.module.erp.dal.dataobject.logistics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("erp_logistics_exception")
@KeySequence("erp_logistics_exception_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpLogisticsExceptionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long relationId;
    private Long nodeId;
    private String exceptionCode;
    private String exceptionName;
    private String description;
    private String status;
    private LocalDateTime occurredAt;
    private Long handlerUserId;
    private String handleResult;
    private LocalDateTime handleTime;

}
