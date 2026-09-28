package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "学员端 - 学习会话心跳 Response VO")
@Data
public class LearnerSessionHeartbeatRespVO {

    @Schema(description = "学习会话 ID", example = "1")
    private Long sessionId;

    /**
     * 会话状态：active 继续计时 / ended 会话已结束（看 reason）/ invalid 会话判无效（看 reason）
     */
    @Schema(description = "会话状态：active/ended/invalid", example = "active")
    private String status;

    @Schema(description = "结束/无效原因：timeout 心跳超时 / clock 心跳过快 / cheat 验证失败 / restart 服务重启", example = "")
    private String reason;

    @Schema(description = "会话累计有效秒数", example = "300")
    private Integer validSeconds;

    @Schema(description = "是否需要人机验证（数学题）", example = "false")
    private Boolean needVerify;

    @Schema(description = "验证题目（needVerify=true 时返回，如 7*8=?）", example = "7*8=?")
    private String challengeQuestion;

    @Schema(description = "本次心跳是否因间隔异常未计入（提示用户保持页面在前台）", example = "false")
    private Boolean warnInvalid;

    @Schema(description = "是否可打卡（有效学习满 15 秒且本会话未打卡）", example = "true")
    private Boolean canCheckin;

    @Schema(description = "文档已覆盖页数（document 会话返回）", example = "3")
    private Integer docCoveredPages;

    @Schema(description = "文档总页数（document 会话返回）", example = "10")
    private Integer docTotalPages;

}
