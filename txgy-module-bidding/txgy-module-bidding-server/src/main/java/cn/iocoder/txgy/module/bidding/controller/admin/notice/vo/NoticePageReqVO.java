package cn.iocoder.txgy.module.bidding.controller.admin.notice.vo;
// 规范依据：《招投标模块开发规范》§3.1/§4.6（关键词走检索组件）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 公告/公示分页查询入参。
 * 说明：keyword 全文关键词（公告正文/项目信息）走检索组件（规范 4.6）；
 * 未启用检索组件时关键词查询直接报错提示，业务表不做全文 LIKE。
 */
@Schema(description = "管理后台 - 公告/公示分页查询入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticePageReqVO extends PageParam {

    @Schema(description = "关键词（公告标题/项目名称/编号；全文命中需启用检索组件）", example = "安置房")
    private String keyword;

    @Schema(description = "公告/公示类型 code", example = "TENDER_NOTICE")
    private String noticeType;

    @Schema(description = "状态", example = "PUBLISHED")
    private String status;

    @Schema(description = "仅查询企业内部公告；null=全部（公开+内部）", example = "false")
    private Boolean internalFlag;

    @Schema(description = "公开级别", example = "SOCIAL")
    private String publicLevel;

    @Schema(description = "项目编号")
    private String projectCode;

    @Schema(description = "招标项目编号（精确匹配；权威数据集 TENDER_PROJECT_CODE）")
    private String tenderProjectCode;

    @Schema(description = "标段（包）编号（精确匹配；权威数据集 BID_SECTION_CODES，任一分量命中即算）")
    private String bidSectionCode;

    @Schema(description = "发布时间-开始")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime publishTimeStart;

    @Schema(description = "发布时间-结束")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime publishTimeEnd;

}
