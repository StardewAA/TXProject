package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 证件/资质生命周期流程发起入参（变更 / 续期 / 注销吊销 / 年检 四类共用）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 3（审查，含 ANNUAL 年检）、表 5（续期：old/new 有效期、
 * 新文件版本、办结回写）、表 6（变更：change_group_id 归组 + 字段级 old/new）、表 7（吊销/注销/暂扣/恢复）；
 * 用户范围口径：做到"证照全生命周期管理（中级）"，**不做签发**。
 * <p>为什么这样写：四类流程共用"申请单号 + 附件 + 备注 + 分类字段"骨架，用同一入参对象 +
 * 按 lifecycleType 取用相应字段，可让前端只对接一个提交接口、后端只维护一套状态机校验；
 * 各类型必填项由 Service 按类型校验并给出明确错误码（CERT_LIFECYCLE_TARGET_REQUIRED）。
 * <p>业务目标：让企业每一次换证、年检、注销都有申请留痕，且办结自动回写台账，不再靠人工改 Excel。
 */
@Schema(description = "管理后台 - 证件生命周期流程发起入参")
@Data
public class CertLifecycleApplyReqVO {

    @Schema(description = "业务对象域 code：CERT 证照台账/PERSON_CERT 员工证书/ENTERPRISE_QUAL 企业资质/SAFETY_PERMIT 安许证",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "CERT")
    @NotBlank(message = "业务对象域不能为空")
    private String bizDomain;

    @Schema(description = "业务对象主键（证照/证书/资质/安许证 id）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "业务对象不能为空")
    private Long bizId;

    @Schema(description = "生命周期类型 code：CHANGE 变更/RENEW 续期/REVOKE 注销吊销/ANNUAL 年检",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "RENEW")
    @NotBlank(message = "生命周期类型不能为空")
    private String lifecycleType;

    @Schema(description = "申请单号（同对象同类型内唯一，用于幂等防重复发起）", example = "BL-2026-0001")
    private String applyNo;

    // ===== 变更（lifecycleType=CHANGE）=====
    @Schema(description = "变更字段清单（一行一字段，服务层自动归组 change_group_id）")
    private List<FieldChange> changes;

    @Schema(description = "变更摘要", example = "企业名称变更后同步证照名称")
    private String changeSummary;

    // ===== 续期（lifecycleType=RENEW）=====
    @Schema(description = "续期类型 code：RENEW 延续/REISSUE 补换证", example = "RENEW")
    private String renewalType;

    @Schema(description = "续期后有效期起始")
    private LocalDate newValidFrom;

    @Schema(description = "续期后有效期止（生效回写台账，并驱动下一轮到期预警）")
    private LocalDate newValidTo;

    @Schema(description = "续期后下次年检日（生效回写台账）")
    private LocalDate newNextReviewDate;

    @Schema(description = "新证书编号（补换证产生新编号时填写）")
    private String newCertNo;

    @Schema(description = "批复/许可决定文号")
    private String resultNo;

    // ===== 注销/吊销（lifecycleType=REVOKE）=====
    @Schema(description = "注销吊销类型 code：REVOKE 吊销/CANCEL 注销/SUSPEND 暂扣/RESTORE 恢复", example = "CANCEL")
    private String revokeType;

    @Schema(description = "决定机关")
    private String revokeAuthority;

    @Schema(description = "决定事由")
    private String revokeReason;

    @Schema(description = "决定生效日期")
    private LocalDate revokeEffectiveAt;

    // ===== 年检（lifecycleType=ANNUAL）=====
    @Schema(description = "年检年度", example = "2026")
    private Integer reviewYear;

    @Schema(description = "年检日期")
    private LocalDate reviewDate;

    @Schema(description = "年检结论：1 通过 / 0 不通过", example = "1")
    private Integer reviewResult;

    // ===== 公共 =====
    @Schema(description = "新文件 id（变更页/延续页/新证扫描件，经 FileApi 上传后登记）")
    private Long newCertFileId;

    @Schema(description = "佐证材料文件 id 列表（JSON 数组字符串）", example = "[1001,1002]")
    private String evidenceFileIds;

    @Schema(description = "备注")
    private String remark;

    /** 单项字段变更（old/new 均以字符串承载，数值/日期按主档口径序列化）。 */
    @Schema(description = "单项字段变更")
    @Data
    public static class FieldChange {

        @Schema(description = "台账字段名（如 certName/validTo/certLevel/certStatus）", example = "certLevel")
        private String fieldName;

        @Schema(description = "变更前值")
        private String oldValue;

        @Schema(description = "变更后值（生效时回写主档）")
        private String newValue;
    }
}
