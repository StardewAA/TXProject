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
 * <p>关键词全文检索不在此查询：外部公告"审核转 ACTIVE"后由 MQ 事件驱动建 ES 索引
 * （见 NoticeExternalIndexProducer 预留，规范 §4.6），keyword 参数走检索组件；本接口只做结构化过滤。
 */
@Schema(description = "管理后台 - 外部公告分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeExternalPageReqVO extends PageParam {

    @Schema(description = "标题（前缀精确匹配，不开放正文 LIKE；全文走 ES 检索组件）")
    private String title;

    @Schema(description = "来源站 code")
    private String sourceCode;

    @Schema(description = "内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER")
    private String contentType;

    @Schema(description = "防腐保温行业判定：BELONGS/NOT_BELONGS/UNCERTAIN")
    private String industryFlag;

    @Schema(description = "关联项目编号（跟踪维度）")
    private String projectCode;

    @Schema(description = "公告类型（外站原文/权威码均可）")
    private String noticeTypeRaw;

    @Schema(description = "开始发布时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime beginTime;

    @Schema(description = "结束发布时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime endTime;

}
