package cn.iocoder.txgy.module.hrm.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - HRM 体检记录 Response VO")
@Data
public class HrmHealthCheckRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "员工编号", example = "124")
    private Long employeeId;

    @Schema(description = "姓名", example = "吴斌")
    private String employeeName;

    @Schema(description = "工号", example = "QC000006")
    private String jobNumber;

    @Schema(description = "体检类型：1 入职，2 在岗，3 离职，4 职业健康")
    private Integer checkType;

    @Schema(description = "体检类型名称", example = "入职")
    private String checkTypeName;

    @Schema(description = "体检日期")
    private LocalDate checkDate;

    @Schema(description = "体检机构")
    private String institution;

    @Schema(description = "体检结论：1 合格，2 不合格，3 需复查")
    private Integer result;

    @Schema(description = "体检结论名称", example = "合格")
    private String resultName;

    @Schema(description = "复查日期")
    private LocalDate recheckDate;

    @Schema(description = "有效期至")
    private LocalDate validUntil;

    @Schema(description = "是否在有效期内")
    private Boolean valid;

    @Schema(description = "附件")
    private List<String> fileUrls;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
