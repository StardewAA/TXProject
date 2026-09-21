package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.insurancededuct;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 社保代扣取数 Response VO
 *
 * <p>月度工资表生成时，把"个人社保 + 个人公积金"作为两个代扣项带进工资表，
 * 比例沿用「社保管理 → 社保方案」里已配好的比例，不需要在 HRM 里重复配置。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 社保代扣取数 Response VO")
@Data
public class HrmSalaryInsuranceDeductRespVO {

    @Schema(description = "员工编号", example = "131")
    private Long employeeId;
    @Schema(description = "员工姓名", example = "李保温")
    private String employeeName;
    @Schema(description = "工号", example = "QC000011")
    private String jobNumber;
    @Schema(description = "年份", example = "2026")
    private Integer year;
    @Schema(description = "月份", example = "8")
    private Integer month;
    @Schema(description = "个人社保", example = "696.00")
    private BigDecimal personalInsuranceAmount;
    @Schema(description = "个人公积金", example = "240.00")
    private BigDecimal personalProvidentFundAmount;
    @Schema(description = "代扣合计", example = "936.00")
    private BigDecimal totalDeductAmount;
    @Schema(description = "是否找到当月社保记录", example = "true")
    private Boolean found;

}
