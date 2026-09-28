package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 工号规则 Response VO")
@Data
public class HrmEmployeeNoRuleRespVO {

    @Schema(description = "规则编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "规则名称", example = "秦川默认工号规则")
    private String name;

    @Schema(description = "租户缩写（工号前缀）", example = "QC")
    private String tenantAbbr;

    @Schema(description = "顺序号位数（左补零）", example = "6")
    private Integer serialLength;

    @Schema(description = "前缀与序号之间的分隔符", example = "-")
    private String prefixSeparator;

    @Schema(description = "重置周期：0 不重置，1 按年重置", example = "0")
    private Integer resetPeriod;

    @Schema(description = "是否允许人工指定工号", example = "false")
    private Boolean allowManual;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "规则示例（按当前配置拼出的工号样例）", example = "QC000001")
    private String sampleJobNumber;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
