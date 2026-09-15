package cn.iocoder.txgy.module.cms.controller.admin.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CMS 智能查询记录 Response VO")
@Data
public class CmsSearchRespVO {

    @Schema(description = "记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "内容类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "standard")
    private String type;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "GB/T 21448 埋地钢质管道")
    private String title;

    @Schema(description = "审核状态", example = "1")
    private Integer auditStatus;

    @Schema(description = "发布状态", example = "1")
    private Integer publishStatus;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
