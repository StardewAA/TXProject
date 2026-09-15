package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo;
// [依据] 下载器人工触发汇总出参（对应 CrawlSourceRunner.RunSummary）。

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 采集执行汇总 Response")
@Data
public class CrawlRunRespVO {

    @Schema(description = "采集源 code")
    private String sourceCode;
    @Schema(description = "开始/结束时间")
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @Schema(description = "成功落库（新增）条数")
    private Integer totalIngested;
    @Schema(description = "失败条数")
    private Integer totalFailed;
    @Schema(description = "各栏目明细")
    private List<Category> categories;

    @Schema(description = "栏目明细行")
    @Data
    public static class Category {
        @Schema(description = "栏目名")
        private String name;
        @Schema(description = "列表抓到条数")
        private Integer fetched;
        @Schema(description = "新增落库数")
        private Integer ingested;
        @Schema(description = "失败数")
        private Integer failed;
        @Schema(description = "栏目级错误（可选）")
        private String error;
    }

}
