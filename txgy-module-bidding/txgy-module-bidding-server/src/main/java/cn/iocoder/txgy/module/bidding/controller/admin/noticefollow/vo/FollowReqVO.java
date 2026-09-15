package cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo;
// 规范依据：设计文档 v0.6 §12.4/§13.5；《招投标模块开发规范》§3.1（VO 规约）

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "关注请求")
@Data
public class FollowReqVO {

    @Schema(description = "关注目标类型：ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "关注目标类型不能为空")
    private String targetType;

    @Schema(description = "目标键：公告 id 或 项目/招标项目/标段 维度代码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "关注目标键不能为空")
    private String targetKey;

}
