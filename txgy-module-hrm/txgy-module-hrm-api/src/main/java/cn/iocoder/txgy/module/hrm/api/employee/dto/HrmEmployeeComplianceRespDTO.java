package cn.iocoder.txgy.module.hrm.api.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工上岗合规状态 Response DTO
 *
 * <p>供 MES（现场执行）在"人员进场 / 派工上岗"前调用，判断这个人能不能进这个场。
 * HRM 只负责回答"人的资质是否合格"，"能不能进这个项目"由 MES 结合项目要求判断。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC - 员工上岗合规状态 Response DTO")
@Data
public class HrmEmployeeComplianceRespDTO {

    @Schema(description = "员工编号", example = "1024")
    private Long employeeId;

    @Schema(description = "工号", example = "QC000012")
    private String jobNumber;

    @Schema(description = "姓名", example = "李四")
    private String name;

    @Schema(description = "手机号", example = "13800000000")
    private String mobile;

    @Schema(description = "在职状态：1 在职，2 待入职，3 待离职，4 离职", example = "1")
    private Integer entryStatus;

    @Schema(description = "在职状态名称", example = "在职")
    private String entryStatusName;

    @Schema(description = "是否在职（可上岗的前提）", example = "true")
    private Boolean onJob;

    // ==================== 黑名单 ====================

    @Schema(description = "是否命中黑名单", example = "false")
    private Boolean blacklistHit;

    @Schema(description = "黑名单原因", example = "高空作业未系安全带，严重安全违规")
    private String blacklistReason;

    // ==================== 证照 ====================

    @Schema(description = "证照总数", example = "3")
    private Integer certificateTotal;

    @Schema(description = "有效证照数", example = "2")
    private Integer certificateValidCount;

    @Schema(description = "失效/过期的证照名称", example = "[\"高处作业操作证\"]")
    private List<String> invalidCertificates;

    // ==================== 三级安全教育 ====================

    @Schema(description = "三级安全教育是否完成（HRM 侧校验公司级；项目级/班组级由 MES 结合项目校验）", example = "true")
    private Boolean safetyEducationPassed;

    @Schema(description = "已完成合格的教育级别名称", example = "[\"公司级\"]")
    private List<String> passedEducationLevels;

    @Schema(description = "缺失的教育级别名称", example = "[\"项目级\",\"班组级\"]")
    private List<String> missingEducationLevels;

    // ==================== 体检 ====================

    @Schema(description = "体检是否合格且在有效期内", example = "true")
    private Boolean healthCheckPassed;

    @Schema(description = "最近一次体检有效期至", example = "2027-03-01")
    private LocalDate healthValidUntil;

    // ==================== 总体结论 ====================

    @Schema(description = "综合是否合格（在职 + 非黑名单 + 证照有效 + 安全教育 + 体检）", example = "false")
    private Boolean passed;

    @Schema(description = "不合格原因列表", example = "[\"证照过期：高处作业操作证\"]")
    private List<String> reasons;

    @Schema(description = "校验时间")
    private LocalDateTime checkedAt;

}
