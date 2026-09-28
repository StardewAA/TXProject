package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 安全生产许可证行（页签 4）。
 *
 * <p>规范出处：契约 §4 G7 与同目录 v1.1《安许风险联动》；表 14-8 + 风险联动 ALTER。
 * <p>为什么这样写：① 风险三件套（{@code riskFlag}/{@code riskLevel}/{@code riskEvaluatedAt}）与
 * 宽限登记（{@code graceExpireAt}/{@code graceReason}/{@code graceSourceRef}）一并返回——
 * 风险联动页正是按这些字段渲染风险卡，缺一个就会出现"风险未知"降级；
 * ② {@code daysToExpire} 由后端按统一口径算（null=未填有效期），避免各页各算；
 * ③ {@code frequentAlertCount} + {@code frequentWindowStart} 支撑"一年内频繁预警"标签（人员风险口径同源）。
 * <p>业务目标：安许台账与风险结论同屏，安全管理人员一眼看清"还能不能用、风险几级、宽限到几时"。
 */
@Data
public class QualificationPermitRespVO {

    @Schema(description = "安许证 id", example = "6001")
    private Long id;

    @Schema(description = "企业主体 id", example = "1")
    private Long entId;

    @Schema(description = "许可证编号", example = "（陕）JZ安许证字〔2023〕001234")
    private String permitNo;

    @Schema(description = "发证机关", example = "陕西省住建厅")
    private String issuingAuthority;

    @Schema(description = "发证日期", example = "2023-06-01")
    private LocalDate issueDate;

    @Schema(description = "有效期至（null=未填）", example = "2026-05-31")
    private LocalDate validTo;

    @Schema(description = "距到期天数（null=未填有效期；负数=已过期）", example = "120")
    private Integer daysToExpire;

    @Schema(description = "许可状态 code（字典 safety_permit_status）", example = "EXPIRING_SOON")
    private String permitStatus;

    @Schema(description = "法定代表人", example = "李四")
    private String legalPerson;

    @Schema(description = "绑定证照台账 id", example = "530")
    private Long bindedCertId;

    @Schema(description = "风险标记 code（字典 safety_risk_flag：A/B/C）", example = "B")
    private String riskFlag;

    @Schema(description = "风险等级 code（字典 risk_level）", example = "WARNING")
    private String riskLevel;

    @Schema(description = "风险重算时间", example = "1788668723000")
    private LocalDateTime riskEvaluatedAt;

    @Schema(description = "宽限截止时间（null=未登记宽限）", example = "1788668723000")
    private LocalDateTime graceExpireAt;

    @Schema(description = "宽限原因", example = "换证在办")
    private String graceReason;

    @Schema(description = "宽限依据出处", example = "陕建许〔2026〕12 号")
    private String graceSourceRef;

    @Schema(description = "频繁预警次数（窗口内）", example = "2")
    private Integer frequentAlertCount;

    @Schema(description = "频繁预警窗口起始", example = "1788668723000")
    private LocalDateTime frequentWindowStart;

    @Schema(description = "风险规则版本（重算依据，便于解释结论）", example = "v2026.1")
    private String ruleVersion;

    @Schema(description = "备注", example = "换证材料已提交")
    private String remark;

    @Schema(description = "乐观锁版本（状态流转必须回传）", example = "3")
    private Integer version;
}
