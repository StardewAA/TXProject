package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import cn.iocoder.txgy.framework.excel.core.annotations.DictFormat;
import cn.iocoder.txgy.framework.excel.core.convert.DictConvert;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 项目 Response VO")
@Data
@ExcelIgnoreUnannotated
public class MesProProjectRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "项目编码", example = "PRJ20260902001")
    @ExcelProperty("项目编码")
    private String code;

    @Schema(description = "项目名称", example = "某防腐保温工程")
    @ExcelProperty("项目名称")
    private String name;

    @Schema(description = "来源单据编号", example = "SO-202609-001")
    @ExcelProperty("来源单据编号")
    private String orderSourceCode;

    @Schema(description = "项目来源类型", example = "1")
    @ExcelProperty(value = "项目来源类型", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.MES_PRO_PROJECT_SOURCE_TYPE)
    private Integer sourceType;

    @Schema(description = "项目状态", example = "0")
    @ExcelProperty(value = "项目状态", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.MES_PRO_PROJECT_STATUS)
    private Integer status;

    @Schema(description = "项目优先级", example = "3")
    @ExcelProperty(value = "项目优先级", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.MES_PRO_PROJECT_PRIORITY)
    private Integer priority;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "负责人", example = "张三")
    @ExcelProperty("负责人")
    private String owner;

    @Schema(description = "责任部门", example = "生产管理部")
    @ExcelProperty("责任部门")
    private String dept;

    @Schema(description = "计划开始时间", example = "2026-09-01 00:00:00")
    @ExcelProperty("计划开始时间")
    private LocalDateTime planStartTime;

    @Schema(description = "计划结束时间", example = "2026-12-31 00:00:00")
    @ExcelProperty("计划结束时间")
    private LocalDateTime planEndTime;

    @Schema(description = "实际开始时间", example = "2026-09-05 00:00:00")
    @ExcelProperty("实际开始时间")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间", example = "2026-12-28 00:00:00")
    @ExcelProperty("实际结束时间")
    private LocalDateTime actualEndTime;

    @Schema(description = "工期天数", example = "120")
    @ExcelProperty("工期(天)")
    private Integer durationDays;

    @Schema(description = "备注", example = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "关联生产工单数", example = "3")
    private Integer workOrderCount;

    @Schema(description = "已完成生产工单数", example = "1")
    private Integer finishedWorkOrderCount;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
