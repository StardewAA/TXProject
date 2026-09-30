package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "员工端 - 求职助理对话 Response VO")
@Data
public class HrmPortalRecruitAgentChatRespVO {

    @Schema(description = "模型回复的纯文本")
    private String reply;

    /**
     * 这条回复是不是在给用户列岗位。
     *
     * <p>由模型在回答末尾加 {@code [JOBS]} 标记决定（服务端会把标记摘掉再返回）。
     * 不能无条件把岗位卡片挂到每条回复下面 —— 用户问「简历怎么写」时弹十张岗位卡是噪音。</p>
     */
    @Schema(description = "这条回复是否在给用户列岗位，前端据此决定要不要渲染岗位卡片")
    private Boolean listing;

    @Schema(description = "本轮的岗位卡片，仅在 listing 为 true 时返回")
    private List<HrmPortalRecruitAgentChatJobVO> jobs;

    @Schema(description = "模型标识，用于排查「这条回复是哪次调用产生的」")
    private String model;

    @Schema(description = "提示词版本")
    private String promptVersion;

    @Schema(description = "固定展示的免责说明")
    private String disclaimer = "AI 生成内容仅供参考，请核实后使用";
}
