package cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo;
// 规范依据：设计文档 v0.6 §四/§六（源配置白名单/频控/通道/策略）；《招投标模块开发规范》§3.1（VO 规约）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 采集源分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlSourcePageReqVO extends PageParam {

    @Schema(description = "源代码（精确）")
    private String sourceCode;

    @Schema(description = "源名称（模糊，管理端配置检索）")
    private String sourceName;

    @Schema(description = "通道 code（精确）")
    private String channelCode;

    @Schema(description = "启用状态（true/false/空=全部）")
    private Boolean enabled;

}
