package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 企业资质台账行（分页列表出参）。
 *
 * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G1（字段逐条对应）；§四-3（日期语义字段用 date）。
 *
 * <p>为什么这样写：① 出参带 {@code version}——列表行的编辑/延续/流转都必须回传乐观锁版本，
 * 否则前端只能再查一次详情才能提交；② 出参带 {@code daysToExpire}——剩余天数由后端按统一口径计算，
 * 避免前端各页各算导致"有的地方过期了还显示 0 天"；③ {@code hasPendingApply} 让前端直接决定
 * 「延续申报」按钮是否可用，不需要另查申报单列表。
 *
 * <p>业务目标：台账列表一次拿全渲染与按钮态判断所需字段，减少前端往返与口径分歧。
 */
@Data
public class QualificationRespVO {

    @Schema(description = "资质主档 id", example = "9001")
    private Long id;

    @Schema(description = "企业主体 id", example = "1")
    private Long entId;

    @Schema(description = "资质序列 code", example = "CONSTRUCTION")
    private String qualSerial;

    @Schema(description = "资质类别 code", example = "GENERAL_CONTRACTING")
    private String qualCategory;

    @Schema(description = "资质等级 code", example = "FIRST")
    private String qualGrade;

    @Schema(description = "资质证书编号", example = "D361123456")
    private String certNo;

    @Schema(description = "发证/许可机关", example = "陕西省住建厅")
    private String issuingAuthority;

    @Schema(description = "发证日期", example = "2024-05-20")
    private LocalDate issueDate;

    @Schema(description = "有效期至（null=长期）", example = "2026-05-19")
    private LocalDate validTo;

    @Schema(description = "批准承包/承揽范围", example = "可承担单项合同额不超过企业注册资本金5倍的市政公用工程")
    private String approvalScope;

    @Schema(description = "资质状态 code", example = "EXPIRING_SOON")
    private String qualStatus;

    @Schema(description = "距到期天数（null=长期或未填有效期；负数=已过期）", example = "62")
    private Integer daysToExpire;

    @Schema(description = "绑定的证照台账 id（影像/验真入口）", example = "501")
    private Long bindedCertId;

    @Schema(description = "是否存在待审批的延续申报单（true 时「延续申报」按钮应置灰）", example = "false")
    private Boolean hasPendingApply;

    @Schema(description = "关联人员数（要素台账 qualification_staff_config 按 qualId 计数；五页契约 Q8，2026-09-28）", example = "3")
    private Long staffCount;

    @Schema(description = "数据来源渠道：MANUAL 手工登记 / SYNC 同步 / IMPORT 导入", example = "MANUAL")
    private String sourceChannel;

    @Schema(description = "备注", example = "到期前 90 天启动延续")
    private String remark;

    @Schema(description = "乐观锁版本（编辑/流转必须回传）", example = "3")
    private Integer version;
}
