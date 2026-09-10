package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 培训班学员 Excel 导入 VO
 *
 * <p>匹配规则：以【手机号】精确匹配系统用户作为学员；姓名列仅作可读性提示。
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 培训班学员 Excel 导入 VO")
@Data
public class ClassStudentImportExcelVO {

    @Schema(description = "学员姓名（可读性提示，可不填）", example = "张三")
    @ExcelProperty("姓名")
    private String name;

    @Schema(description = "手机号（必填，用于匹配系统账号）", example = "13800000000")
    @ExcelProperty("手机号")
    private String mobile;

}
