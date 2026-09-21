package cn.iocoder.txgy.module.infra.dal.dataobject.worklog;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Business audit archive. No business update/delete operations or retention cleanup. */
@TableName("infra_work_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkLogDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private String userName;
    private String module;
    private String action;
    private String businessKey;
    private String summary;
    private String requestParams;
    private String changes;
    private Integer resultCode;
    private String resultMsg;
    private String userIp;
    private String userAgent;
    private LocalDateTime beginTime;
    private Integer duration;
    private String traceId;
    private String requestUrl;
    private String requestMethod;
}
