package cn.iocoder.txgy.module.cms.controller.admin.search.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CMS 智能查询分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsSearchPageReqVO extends PageParam {

    @Schema(description = "内容类型", example = "standard", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type; // standard / material / process / quota / policy

    @Schema(description = "关键词（标题模糊匹配）", example = "防腐")
    private String keyword;

    @Schema(description = "审核状态（0待审核 1通过 2驳回 3草稿）", example = "1")
    private Integer auditStatus;

    @Schema(description = "发布状态（0未发布 1已发布 2已下架）", example = "1")
    private Integer publishStatus;

    @Schema(description = "发布年份（高级筛选，如 2026）", example = "2026")
    private Integer publishYear;

}
