package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 员工社保缴纳记录登记入参。
 *
 * <p>规范出处：用户业务口径"核验员工社保缴纳单位是否与申报企业完全一致，杜绝人证分离和挂证"；
 * 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（social_security_status/social_security_tenant）。
 * <p>业务目标：以"缴纳单位 + 缴纳月份"构成可核验证据链（一致性核验的关键输入）。
 */
@Schema(description = "管理后台 - 员工社保缴纳记录登记入参")
@Data
public class PersonnelSocialSecuritySaveReqVO {

    @Schema(description = "缴纳单位（本企业主体 id）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "缴纳单位不能为空")
    private Long entId;

    @Schema(description = "参保员工", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "参保员工不能为空")
    private Long personId;

    @Schema(description = "缴纳单位名称（照抄社保明细）", example = "陕西××建设集团有限公司")
    private String payerName;

    @Schema(description = "缴纳单位统一社会信用代码（与申报企业不一致即判人证分离）", example = "91610000MA6XXXXXXX")
    private String payerCreditCode;

    @Schema(description = "缴纳月份（yyyy-MM）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-08")
    @NotBlank(message = "缴纳月份不能为空")
    private String payMonth;

    @Schema(description = "缴纳状态 code：NORMAL/SUSPENDED/TRANSFERRED/NONE", example = "NORMAL")
    private String payStatus;

    @Schema(description = "缴费基数")
    private BigDecimal payBase;

    @Schema(description = "缴费金额")
    private BigDecimal payAmount;

    @Schema(description = "社保个人编号")
    private String socialNo;

    @Schema(description = "数据来源 code：MANUAL/IMPORT/API_SYNC")
    private String sourceChannel;

    @Schema(description = "参保证明文件 id")
    private Long evidenceFileId;

    @Schema(description = "备注")
    private String remark;
}
