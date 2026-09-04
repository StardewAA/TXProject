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
