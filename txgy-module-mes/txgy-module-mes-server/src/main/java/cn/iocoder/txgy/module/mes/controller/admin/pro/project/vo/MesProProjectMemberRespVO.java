package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.iocoder.txgy.framework.excel.core.annotations.DictFormat;
import cn.iocoder.txgy.framework.excel.core.convert.DictConvert;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - MES 项目成员 Response VO")
@Data
@ExcelIgnoreUnannotated
public class MesProProjectMemberRespVO {

    @Schema(description = "编号", example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "项目编号", example = "1024")
    private Long projectId;

    @Schema(description = "成员用户编号", example = "1")
    private Long userId;

    @Schema(description = "成员姓名", example = "张三")
    @ExcelProperty("成员姓名")
    private String userName;

    @Schema(description = "项目角色", example = "1")
    @ExcelProperty(value = "项目角色", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.MES_PRO_PROJECT_MEMBER_ROLE)
    private Integer role;

    @Schema(description = "备注", example = "备注")
    @ExcelProperty("备注")
    private String remark;

}
