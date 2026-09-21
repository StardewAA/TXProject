package cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 工具报废单 Response VO")
@Data
public class MesTmToolScrapRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "报废单编码")
    private String code;

    @Schema(description = "报废单名称")
    private String name;

    @Schema(description = "报废日期")
    private LocalDateTime scrapDate;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "报废原因")
    private String scrapReason;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
