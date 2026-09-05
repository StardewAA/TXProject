package cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo;
// 规范依据：设计文档 v0.6 §12.4/§13.5；《招投标模块开发规范》§3.1（VO 规约）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "关注项动态/事件 Response")
@Data
public class FollowEventRespVO {

    @Schema(description = "事件 ID")
    private Long id;
    @Schema(description = "来源：INTERNAL/EXTERNAL")
    private String source;
    @Schema(description = "事件类型：NOTICE_PUBLISHED/NOTICE_UPDATED/EXTERNAL_ACTIVE/STAGE_CHANGE")
    private String eventType;
    @Schema(description = "关联公告 ID（可空）")
    private Long noticeId;
    @Schema(description = "标题快照")
    private String titleSnapshot;
    @Schema(description = "原网址（外部公告）")
    private String origUrl;
    @Schema(description = "动态发生时间")
    private LocalDateTime occurredAt;
    @Schema(description = "是否已读")
    private Boolean readFlag;

}
