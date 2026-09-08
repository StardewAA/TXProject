package cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo;
// 规范依据：设计文档 v0.6 §三（task 状态机/重试）；《招投标模块开发规范》§3.1（VO 规约）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 采集任务分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlTaskPageReqVO extends PageParam {

    @Schema(description = "所属批次 ID（bidding_crawl_job.id）")
    private Long jobId;

    @Schema(description = "源代码（精确）")
    private String sourceCode;

    @Schema(description = "状态：PENDING/RUNNING/SUCCESS/FAILED/SKIPPED")
    private String status;

    @Schema(description = "通道 code（精确）")
    private String channelCode;

    @Schema(description = "URL 模糊（管理端检索用，定位死信/重试对象）")
    private String url;

    @Schema(description = "开始创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime beginTime;

    @Schema(description = "结束创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime endTime;

}
