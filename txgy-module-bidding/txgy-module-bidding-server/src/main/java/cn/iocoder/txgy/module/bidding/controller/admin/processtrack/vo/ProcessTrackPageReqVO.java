package cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo;

// [契约]《后端变更请求-投标流程跟踪管理端总览-2026-09-18.md》§5 接口1
//      + 《后端变更请求-投标与资格预审多文件化-2026-10-02.md》R5（bidSectionCode 可选，标段聚合投标记录）
// 入参：pageNo/pageSize/keyword（公司·项目名·编号·招标人·负责人 包含匹配）/state/tenantId（仅超管有效）/ownerUserId/bidSectionCode

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 投标流程跟踪总览分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessTrackPageReqVO extends PageParam {

    @Schema(description = "关键词（公司名·项目名·编号·招标人·负责人 包含匹配）")
    private String keyword;

    @Schema(description = "阶段 code（INTENTION/QUALIFICATION_REVIEW/PENDING_BID/BIDDED/TERMINATED）")
    private String state;

    @Schema(description = "租户ID（仅超管有效；租户视角后端强制忽略）")
    private Long tenantId;

    @Schema(description = "负责人用户ID（精确匹配）")
    private Long ownerUserId;

    @Schema(description = "标段编号（精确匹配；标段详情页按标段聚合投标记录，契约多文件化 R5）")
    private String bidSectionCode;
}
