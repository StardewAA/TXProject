package cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 工具归还单 Response VO")
@Data
public class MesTmToolReturnRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "归还单编码")
    private String code;

    @Schema(description = "归还单名称")
    private String name;

    @Schema(description = "工位ID")
    private Long workstationId;

    @Schema(description = "工位名称")
    private String workstationName;

    @Schema(description = "归还日期")
    private LocalDateTime returnDate;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
