package cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo;
// 规范依据：设计文档 v0.6 §12.4/§13.5；《招投标模块开发规范》§3.1（VO 规约）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "我的关注项目列表行 Response")
@Data
public class FollowItemRespVO {

    @Schema(description = "关注行 ID")
    private Long id;
    @Schema(description = "维度类型：PROJECT/TENDER_PROJECT/SECTION")
    private String followType;
    @Schema(description = "维度代码")
    private String followKey;
    @Schema(description = "项目/标段名快照")
    private String displayName;
    @Schema(description = "发布单位快照")
    private String issuerName;
    @Schema(description = "进展阶段 code：PENDING/TENDER/OPEN_BID/EVAL/RESULT/CONTRACT/DONE/UNKNOWN")
    private String stageCode;
    @Schema(description = "阶段名称")
    private String stageName;
    @Schema(description = "基础信息快照（JSON）")
    private String baseJson;
    @Schema(description = "最近动态时间")
    private LocalDateTime latestDynamicTime;
    @Schema(description = "未读动态数")
    private Integer unreadCount;
    @Schema(description = "关注时间")
    private LocalDateTime followTime;

}
