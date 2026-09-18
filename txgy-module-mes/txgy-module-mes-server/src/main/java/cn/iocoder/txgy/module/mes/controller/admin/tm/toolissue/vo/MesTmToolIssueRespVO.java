package cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 工具领用单 Response VO")
@Data
public class MesTmToolIssueRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "领用单编码")
    private String code;

    @Schema(description = "领用单名称")
    private String name;

    @Schema(description = "工位ID")
    private Long workstationId;

    @Schema(description = "工位名称")
    private String workstationName;

    @Schema(description = "领用日期")
    private LocalDateTime issueDate;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
