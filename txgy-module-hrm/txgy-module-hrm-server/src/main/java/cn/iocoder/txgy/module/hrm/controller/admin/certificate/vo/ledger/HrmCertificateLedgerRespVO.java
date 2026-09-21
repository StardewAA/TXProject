package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 证件台账 Response VO")
@Data
@Accessors(chain = true)
public class HrmCertificateLedgerRespVO {

    @Schema(description = "证件台账编号", example = "1024")
    private Long id;

    @Schema(description = "员工编号", example = "2048")
    private Long employeeId;

    @Schema(description = "员工姓名", example = "张三")
    private String employeeName;

    @Schema(description = "员工工号", example = "ADMIN-001")
    private String employeeJobNumber;

    @Schema(description = "部门编号", example = "103")
    private Long deptId;

    @Schema(description = "证件类型编号", example = "1")
    private Long typeId;

    @Schema(description = "证件类型名称", example = "特种作业操作证")
    private String typeName;

    @Schema(description = "是否特种作业", example = "true")
    private Boolean specialOperation;

    @Schema(description = "证件名称", example = "低压电工作业操作证")
    private String name;

    @Schema(description = "证件级别", example = "初级")
    private String level;

    @Schema(description = "证件编号", example = "T44030019900718001X")
    private String no;

    @Schema(description = "有效开始日期")
    private LocalDateTime startTime;

    @Schema(description = "有效结束日期")
    private LocalDateTime endTime;

    @Schema(description = "复审日期")
    private LocalDateTime reviewTime;

    @Schema(description = "发证机构", example = "深圳市应急管理局")
    private String issuingAuthority;

    @Schema(description = "发证日期")
    private LocalDateTime issuingTime;

    @Schema(description = "证件附件地址")
    private String attachmentUrl;

    @Schema(description = "证件状态，参见 hrm_certificate_status", example = "1")
    private Integer status;

    @Schema(description = "距到期剩余天数，负数表示已过期", example = "30")
    private Integer remainDays;

    @Schema(description = "待处理预警数量", example = "1")
    private Long pendingWarningCount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "显示顺序", example = "10")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
