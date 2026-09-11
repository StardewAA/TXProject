package cn.iocoder.txgy.module.erp.dal.dataobject.integration;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ERP 供应链协同单业务确认日志
 *
 * 记录接受、拒绝、要求补充资料三类业务确认动作，用于工作台审计与追溯。
 */
@TableName("erp_collaboration_confirm_log")
@KeySequence("erp_collaboration_confirm_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpCollaborationConfirmLogDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long collaborationId;
    private String collaborationNo;
    /** 确认动作：ACCEPTED / REJECTED / NEED_SUPPLEMENT */
    private String action;
    private String reason;
    private Long confirmUserId;
    private String confirmUserName;
    private LocalDateTime createTime;
}
