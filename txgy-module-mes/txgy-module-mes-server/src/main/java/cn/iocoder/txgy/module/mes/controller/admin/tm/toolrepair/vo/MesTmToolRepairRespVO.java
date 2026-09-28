package cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 工具维修单 Response VO")
@Data
public class MesTmToolRepairRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "维修单编码")
    private String code;

    @Schema(description = "维修单名称")
    private String name;

    @Schema(description = "维修日期")
    private LocalDateTime repairDate;

    @Schema(description = "完成日期")
    private LocalDateTime finishDate;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "维修结果")
    private String repairResult;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
