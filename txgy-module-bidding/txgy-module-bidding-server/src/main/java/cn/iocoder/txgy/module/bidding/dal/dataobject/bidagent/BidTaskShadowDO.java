package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * Agent 任务影子状态 DO（§8 风险登记表：任务状态在 Java 侧留一份影子记录）
 */
@TableName("bidding_bid_task_shadow")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class BidTaskShadowDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 任务编号（Agent 侧 taskId）
     */
    private String taskId;

    /**
     * 任务类型：parse / outline / draft / fill
     */
    private String taskType;

    /**
     * 项目编号
     */
    private Long projectId;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 当前阶段
     */
    private String phase;

    /**
     * 进度 0~1
     */
    private BigDecimal progress;

    /**
     * 结果摘要或错误信息
     */
    private String message;

    /**
     * Agent 侧更新时间（ISO 8601 字符串）
     */
    private String agentUpdatedAt;
}
