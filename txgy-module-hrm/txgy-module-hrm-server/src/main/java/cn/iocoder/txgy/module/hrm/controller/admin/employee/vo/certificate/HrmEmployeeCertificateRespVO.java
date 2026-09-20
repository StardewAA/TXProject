package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.certificate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 员工证书 Response VO")
@Data
public class HrmEmployeeCertificateRespVO {

    @Schema(description = "证书编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long employeeId;
    @Schema(description = "证照类型编号", example = "1")
    private Long typeId;
    @Schema(description = "证照类型名称", example = "高处作业操作证")
    private String typeName;
    @Schema(description = "是否特种作业", example = "true")
    private Boolean specialOperation;
    @Schema(description = "是否必持", example = "true")
    private Boolean mandatory;

    @Schema(description = "证书名称", example = "高级工程师")
    private String name;

    @Schema(description = "证书级别", example = "高级")
    private String level;

    @Schema(description = "证书编号", example = "CERT-001")
    private String no;

    @Schema(description = "有效起始日期")
    private LocalDateTime startTime;

    @Schema(description = "有效结束日期")
    private LocalDateTime endTime;
    @Schema(description = "复审日期")
    private java.time.LocalDate reviewDate;

    @Schema(description = "发证机构", example = "人社局")
    private String issuingAuthority;

    @Schema(description = "发证日期")
    private LocalDateTime issuingTime;
    @Schema(description = "证件扫描件地址列表")
    private java.util.List<String> fileUrls;
    @Schema(description = "核验状态：0 未核验，1 已核验，2 核验不通过", example = "1")
    private Integer verifyStatus;
    @Schema(description = "核验状态名称", example = "已核验")
    private String verifyStatusName;
    @Schema(description = "核验时间")
    private LocalDateTime verifyTime;
    @Schema(description = "到期状态：1 有效，2 临期，3 已过期", example = "2")
    private Integer expireStatus;
    @Schema(description = "到期状态名称", example = "临期")
    private String expireStatusName;
    @Schema(description = "距到期天数（负数表示已过期）", example = "37")
    private Long remainDays;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
