package cn.iocoder.txgy.module.hrm.controller.admin.recruit.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 招聘 Agent 任务与结果 Response VO")
@Data
@Accessors(chain = true)
public class HrmRecruitAgentTaskRespVO {

    @Schema(description = "任务编号")
    private Long taskId;

    @Schema(description = "场景：CANDIDATE_SUMMARY 候选人摘要、APPLICATION_MATCH 应聘匹配、INTERVIEW_QUESTION 面试问题建议")
    private String scene;

    @Schema(description = "任务状态：PENDING、RUNNING、SUCCEEDED、FAILED")
    private String status;

    @Schema(description = "关联的应聘记录编号")
    private Long applicationId;

    @Schema(description = "候选人简历快照编号")
    private Long resumeId;

    @Schema(description = "应聘职位编号")
    private Long postId;

    @Schema(description = "分析时的职位内容指纹，职位变更后可据此判断结论是否过期")
    private String postFingerprint;

    @Schema(description = "任务创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "综合得分，无评分场景或未成功时为空")
    private Integer overallScore;

    @Schema(description = "结构化结果")
    private Object result;

    @Schema(description = "模型标识")
    private String model;

    @Schema(description = "提示词版本")
    private String promptVersion;

    @Schema(description = "提示词 token 数，提供方未返回时为空")
    private Integer promptTokens;

    @Schema(description = "输出 token 数")
    private Integer completionTokens;

    @Schema(description = "合计 token 数")
    private Integer totalTokens;

    @Schema(description = "失败错误码")
    private String errorCode;

    @Schema(description = "失败原因")
    private String errorMessage;

    @Schema(description = "固定展示的免责说明")
    private String disclaimer = "AI 辅助分析，仅供参考";
}
