package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - HRM 证照台账 Response VO")
@Data
@ExcelIgnoreUnannotated
public class HrmCertificateLedgerRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "员工编号", example = "119")
    private Long employeeId;

    @Schema(description = "姓名", example = "赵敏")
    @ExcelProperty("姓名")
    private String employeeName;

    @Schema(description = "工号", example = "QC000001")
    @ExcelProperty("工号")
    private String jobNumber;

    @Schema(description = "联系电话", example = "13900007788")
    @ExcelProperty("联系电话")
    private String mobile;

    @Schema(description = "证照类型编号", example = "1")
    private Long typeId;

    @Schema(description = "证照类型", example = "高处作业操作证")
    @ExcelProperty("证照类型")
    private String typeName;

    @Schema(description = "是否特种作业", example = "true")
    private Boolean specialOperation;

    @Schema(description = "是否必持", example = "true")
    private Boolean mandatory;

    @Schema(description = "证件名称", example = "高处作业操作证（登高架设）")
    @ExcelProperty("证件名称")
    private String name;

    @Schema(description = "级别", example = "中级")
    @ExcelProperty("级别")
    private String level;

    @Schema(description = "证号", example = "T610100199001011234")
    @ExcelProperty("证号")
    private String no;

    @Schema(description = "发证机关", example = "陕西省应急管理厅")
    @ExcelProperty("发证机关")
    private String issuingAuthority;

    @Schema(description = "发证日期")
    @ExcelProperty("发证日期")
    private LocalDateTime issuingTime;

    @Schema(description = "有效期起")
    @ExcelProperty("有效期起")
    private LocalDateTime startTime;

    @Schema(description = "有效期止")
    @ExcelProperty("有效期止")
    private LocalDateTime endTime;

    @Schema(description = "复审日期")
    @ExcelProperty("复审日期")
    private LocalDate reviewDate;

    @Schema(description = "到期状态：1 有效，2 临期，3 已过期", example = "2")
    private Integer expireStatus;

    @Schema(description = "到期状态名称", example = "临期")
    @ExcelProperty("到期状态")
    private String expireStatusName;

    @Schema(description = "距到期天数（负数表示已过期）", example = "37")
    @ExcelProperty("距到期天数")
    private Long remainDays;

    @Schema(description = "核验状态：0 未核验，1 已核验，2 核验不通过", example = "1")
    private Integer verifyStatus;

    @Schema(description = "核验状态名称", example = "已核验")
    @ExcelProperty("核验状态")
    private String verifyStatusName;

    @Schema(description = "核验时间")
    private LocalDateTime verifyTime;

    @Schema(description = "证件扫描件")
    private List<String> fileUrls;

    @Schema(description = "备注")
    private String remark;

}
