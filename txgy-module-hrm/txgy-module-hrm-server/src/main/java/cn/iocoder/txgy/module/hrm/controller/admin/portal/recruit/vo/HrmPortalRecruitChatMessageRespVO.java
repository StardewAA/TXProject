package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "员工端 - 招聘沟通消息 Response VO")
@Data
public class HrmPortalRecruitChatMessageRespVO {
    private Long id;
    private Long conversationId;
    private Long senderUserId;
    private Integer contentType;
    private String content;
    private LocalDateTime sendTime;
    private Boolean mine;
}
