package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;
// 规范依据：设计文档 v0.6 §7（列表只出主列）；《招投标模块开发规范》§3.1（VO 规约）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 外部公告分页请求（大厅公开只读）。
 *
 * <p>关键词全文检索（keyword）走检索组件：bidding-notice-external 索引（审核转 ACTIVE 时写入，
 * 见 EsExternalNoticeIndexNotifier，规范 §4.6）；未启用检索组件时传 keyword 直接报错提示，不做业务表 LIKE。
 * 其余字段为结构化精确过滤。
 */
@Schema(description = "管理后台 - 外部公告分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeExternalPageReqVO extends PageParam {

    @Schema(description = "关键词（标题/项目名/编号/正文；走 ES 全文检索组件，需 bidding.es.enabled=true）", example = "炼化")
    private String keyword;

    @Schema(description = "标题（精确匹配筛选，不开放正文 LIKE；全文走 keyword）")
    private String title;

    @Schema(description = "来源站 code")
    private String sourceCode;

    @Schema(description = "内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER")
    private String contentType;

    @Schema(description = "防腐保温行业判定：BELONGS/NOT_BELONGS/UNCERTAIN")
    private String industryFlag;

    @Schema(description = "关联项目编号（跟踪维度）")
    private String projectCode;

    // ===== 契约《关注扇出重放补偿与维度回填-2026-09-29》§四 =====
    // [为什么补] 前端要在"维度关注时间线为空"时做兜底展示（"该维度下已发布的公告如下"），
    //   但公开分页此前只支持 projectCode；传 tenderProjectCode/bidSectionCode 会被**静默丢弃**
    //   （实测：传不存在的值 total 仍为全量 1637）—— 前端若依赖它，会把全量公告当成本维度的，
    //   比不显示更糟。故补齐筛选，与扇出的维度键口径一致。
    // [覆盖率提醒] 实测有效值占比：tender_project_code 1.9%、bid_section_codes 11.3%，
    //   补接口是必要的，但不能指望它解决覆盖率（见契约 §四 结论）。

    @Schema(description = "关联招标项目编号（跟踪维度；与扇出维度键口径一致）")
    private String tenderProjectCode;

    @Schema(description = "关联标段（包）编号（跟踪维度）。bid_section_codes 是分隔串，"
            + "按 FIND_IN_SET 语义匹配；分隔符兼容半角分号/逗号与中文分号「；」/顿号「、」")
    private String bidSectionCode;

    @Schema(description = "公告类型（外站原文/权威码均可）")
    private String noticeTypeRaw;

    @Schema(description = "开始发布时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime beginTime;

    @Schema(description = "结束发布时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime endTime;

}
