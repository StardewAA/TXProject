package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import cn.idev.excel.annotation.ExcelProperty;
import cn.iocoder.txgy.framework.excel.core.annotations.DictFormat;
import cn.iocoder.txgy.framework.excel.core.convert.DictConvert;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MES 项目 Excel 导入 VO
 *
 * @author OPENLAB BS
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MesProProjectImportExcelVO {

    @ExcelProperty("项目编码")
    private String code;

    @ExcelProperty("项目名称")
    private String name;

    @ExcelProperty(value = "项目来源", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.MES_PRO_PROJECT_SOURCE_TYPE)
    private Integer sourceType;

    @ExcelProperty("来源单据编号")
    private String orderSourceCode;

    @ExcelProperty(value = "项目状态", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.MES_PRO_PROJECT_STATUS)
    private Integer status;

    @ExcelProperty(value = "项目优先级", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.MES_PRO_PROJECT_PRIORITY)
    private Integer priority;

    @ExcelProperty("负责人")
    private String owner;

    @ExcelProperty("责任部门")
    private String dept;

    @ExcelProperty("计划开始时间")
    private LocalDateTime planStartTime;

    @ExcelProperty("计划结束时间")
    private LocalDateTime planEndTime;

    @ExcelProperty("实际开始时间")
    private LocalDateTime actualStartTime;

    @ExcelProperty("实际结束时间")
    private LocalDateTime actualEndTime;

    @ExcelProperty("备注")
    private String remark;

}
