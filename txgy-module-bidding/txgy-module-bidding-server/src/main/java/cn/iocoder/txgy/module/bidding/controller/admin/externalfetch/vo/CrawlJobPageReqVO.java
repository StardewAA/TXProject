package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo;
// 规范依据：设计文档 v0.6 §三（批次/汇总）；《招投标模块开发规范》§3.1（VO 规约）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 采集批次分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlJobPageReqVO extends PageParam {

    @Schema(description = "源代码（空=全源批次）")
    private String sourceCode;

    @Schema(description = "触发类型：SCHEDULE/MANUAL/REPAIR")
    private String triggerType;

    @Schema(description = "状态：RUNNING/SUCCESS/PARTIAL/FAILED")
    private String status;

}
