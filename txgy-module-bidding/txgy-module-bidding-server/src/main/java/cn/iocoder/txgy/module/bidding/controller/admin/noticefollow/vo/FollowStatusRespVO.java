package cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo;
// 规范依据：设计文档 v0.6 §12.2；《招投标模块开发规范》§3.1（VO 规约）
// 新增（前端配合说明 N2②）：详情页关注按钮“是否已关注”单点查询出参（替代前端逐页扫描探测）。

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "关注状态查询 Response（目标是否已关注 + 关注行信息）")
@Data
public class FollowStatusRespVO {

    @Schema(description = "是否已关注（FOLLOWING）")
    private Boolean followed;

    @Schema(description = "对应关注行 ID（已关注时非空；用于跳时间线）")
    private Long itemId;

    @Schema(description = "关注目标类型：ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION")
    private String targetType;

    @Schema(description = "目标键（回显入参）")
    private String targetKey;

    @Schema(description = "名称快照（公告级=公告标题/项目名；未建行或为空时 null）")
    private String displayName;

    @Schema(description = "发布单位快照（可为 null）")
    private String issuerName;

}
