package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 沟通会话中求职者的在线简历 Response VO")
@Data
public class HrmRecruitChatCandidateResumeRespVO {

    @Schema(description = "简历来源：SNAPSHOT 投递时的固化快照，ONLINE 求职者当前在线简历", example = "ONLINE")
    private String source;

    @Schema(description = "求职者姓名", example = "张三")
    private String name;

    @Schema(description = "简历标题", example = "求职资料（系统）")
    private String title;

    @Schema(description = "在线简历结构化内容 JSON")
    private String content;

    @Schema(description = "简历时间：快照为投递时间，在线简历为最近更新时间")
    private LocalDateTime updateTime;
}
