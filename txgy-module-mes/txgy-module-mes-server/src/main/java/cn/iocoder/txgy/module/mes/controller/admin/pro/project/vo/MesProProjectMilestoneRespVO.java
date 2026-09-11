package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.iocoder.txgy.framework.excel.core.annotations.DictFormat;
import cn.iocoder.txgy.framework.excel.core.convert.DictConvert;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Schema(description = "管理后台 - MES 项目里程碑 Response VO")
@Data
@ExcelIgnoreUnannotated
public class MesProProjectMilestoneRespVO {

    @Schema(description = "编号", example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "项目编号", example = "1024")
    private Long projectId;

    @Schema(description = "里程碑名称", example = "完成钢管保温加工")
    @ExcelProperty("里程碑名称")
    private String name;

    @Schema(description = "计划完成日期", example = "2026-10-01 00:00:00")
    @ExcelProperty("计划完成日期")
    private LocalDateTime planDate;

    @Schema(description = "实际完成日期", example = "2026-10-03 00:00:00")
    @ExcelProperty("实际完成日期")
    private LocalDateTime actualDate;

    @Schema(description = "里程碑状态", example = "0")
    @ExcelProperty(value = "里程碑状态", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.MES_PRO_PROJECT_MILESTONE_STATUS)
    private Integer status;

    @Schema(description = "排序", example = "1")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "备注", example = "备注")
    @ExcelProperty("备注")
    private String remark;

}
