package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "员工端 - 招聘 Agent 任务与结果 Response VO")
@Data
public class HrmPortalRecruitAgentTaskRespVO {

    @Schema(description = "任务编号")
    private Long taskId;

    @Schema(description = "场景：RESUME_ANALYZE 简历诊断、JOB_MATCH 职位匹配")
    private String scene;

    @Schema(description = "任务状态：PENDING、RUNNING、SUCCEEDED、FAILED")
    private String status;

    @Schema(description = "简历版本编号")
    private Long resumeVersionId;

    @Schema(description = "目标职位编号")
    private Long postId;

    @Schema(description = "分析时的职位内容指纹")
    private String postFingerprint;

    @Schema(description = "任务创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "综合得分，未成功时为空")
    private Integer overallScore;

    @Schema(description = "结构化诊断结果：分项、依据、优势、缺失、风险、建议")
    private Object result;

    @Schema(description = "模型标识")
    private String model;

    @Schema(description = "提示词版本")
    private String promptVersion;

    @Schema(description = "失败错误码")
    private String errorCode;

    @Schema(description = "失败原因")
    private String errorMessage;

    @Schema(description = "固定展示的免责说明")
    private String disclaimer = "AI 辅助分析，仅供参考";
}
