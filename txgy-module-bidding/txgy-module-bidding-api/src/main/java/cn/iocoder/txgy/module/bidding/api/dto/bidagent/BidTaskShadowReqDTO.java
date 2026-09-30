package cn.iocoder.txgy.module.bidding.api.dto.bidagent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Agent 任务影子状态上报（§8 风险登记表：任务状态在 Java 侧留一份影子记录，便于故障恢复）
 */
@Data
public class BidTaskShadowReqDTO {

    /** 租户编号 */
    private Long tenantId;

    /** 项目编号 */
    private Long projectId;

    /** 任务编号 */
    @NotBlank(message = "任务编号不能为空")
    private String taskId;

    /** 任务状态 */
    @NotBlank(message = "任务状态不能为空")
    private String status;

    /** 当前阶段 */
    private String phase;

    /** 进度 0~1 */
    private BigDecimal progress;

    /** 结果摘要或错误信息 */
    private String message;

    /** 更新时间（ISO 8601） */
    private String updatedAt;
}
