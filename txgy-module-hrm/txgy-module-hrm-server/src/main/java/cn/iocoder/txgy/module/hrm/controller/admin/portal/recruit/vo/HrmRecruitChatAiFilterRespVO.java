package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 招聘沟通 AI 筛选 Response VO")
@Data
public class HrmRecruitChatAiFilterRespVO {

    @Schema(description = "AI 对筛选要求的复述，用于向招聘方确认它理解得对不对")
    private String criteria;

    @Schema(description = "命中的沟通会话编号")
    private List<Long> matchedConversationIds;

    @Schema(description = "参与筛选的会话总数")
    private Integer totalCandidates;

    @Schema(description = "模型无法翻译成条件的部分，原文返回给招聘方，避免被静默忽略")
    private List<String> unsupported;

    @Schema(description = "本次解析对应的 Agent 任务编号")
    private Long taskId;

    @Schema(description = "实际使用的模型")
    private String model;

    @Schema(description = "提示词版本")
    private String promptVersion;
}
