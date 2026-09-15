package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 延续申报单（分页列表出参）。
 *
 * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G3；DDL qualification_apply_record。
 *
 * <p>为什么这样写：① 出参带 {@code version}——审批通过/退回必须回传申报单版本；
 * ② 带 {@code targetGrade} / {@code newValidTo} / {@code resultNo}——审批弹窗要回显
 * "原有效期 → 新有效期"与批复文号，不该再查详情；③ {@code ownerBy} 用于显示经办人。
 *
 * <p>业务目标：申报泳道列表直接可渲染、可直接触发审批动作。
 */
@Data
public class QualificationApplyRespVO {

    @Schema(description = "申报单 id", example = "88001")
    private Long id;

    @Schema(description = "企业主体 id", example = "1")
    private Long entId;

    @Schema(description = "资质主档 id", example = "9001")
    private Long qualId;

    @Schema(description = "申报类型 code", example = "RENEW")
    private String applyType;

    @Schema(description = "目标等级 code（升级申报用，延续时为空）", example = "FIRST")
    private String targetGrade;

    @Schema(description = "提交日期", example = "2026-01-05")
    private LocalDate submitDate;

    @Schema(description = "预计办结日期", example = "2026-03-31")
    private LocalDate dueDate;

    @Schema(description = "申报状态 code", example = "SUBMITTED")
    private String status;

    @Schema(description = "批复/许可决定文号", example = "陕建许〔2026〕123 号")
    private String resultNo;

    @Schema(description = "新证书编号", example = "D361123456")
    private String newCertNo;

    @Schema(description = "延续后有效期至", example = "2031-05-19")
    private LocalDate newValidTo;

    @Schema(description = "经办人用户 id", example = "1024")
    private Long ownerBy;

    @Schema(description = "备注（退回原因会追加到备注链）", example = "到期前 90 天启动")
    private String remark;

    @Schema(description = "乐观锁版本（审批必须回传）", example = "1")
    private Integer version;
}
