package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合同到期预警 Response VO
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 劳动合同到期预警 Response VO")
@Data
public class HrmEmployeeContractExpireRespVO {

    @Schema(description = "合同编号", example = "1024")
    private Long id;

    @Schema(description = "合同编号（业务）", example = "HT-2026-0001")
    private String no;

    @Schema(description = "员工编号", example = "131")
    private Long employeeId;

    @Schema(description = "员工姓名", example = "李保温")
    private String employeeName;

    @Schema(description = "工号", example = "QC000011")
    private String jobNumber;

    @Schema(description = "合同类型", example = "1")
    private Integer type;

    @Schema(description = "合同开始时间")
    private LocalDateTime startTime;

    @Schema(description = "合同到期时间")
    private LocalDateTime endTime;

    @Schema(description = "剩余天数（负数表示已过期）", example = "23")
    private Long remainDays;

    @Schema(description = "签署用工主体", example = "秦川化工产业园发展有限公司")
    private String signCompany;

    @Schema(description = "紧急程度：1 一般（>30 天），2 关注（<=30 天），3 紧急（<=7 天）", example = "2")
    private Integer urgency;

    @Schema(description = "紧急程度名称", example = "关注")
    private String urgencyName;

}
