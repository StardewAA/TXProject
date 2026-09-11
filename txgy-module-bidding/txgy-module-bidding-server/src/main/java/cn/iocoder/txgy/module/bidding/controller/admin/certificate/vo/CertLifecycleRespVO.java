package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 证件生命周期记录出参。
 *
 * <p>规范出处：《招投标模块开发规范》§4.4-4（字典存 code）；《电子证件全流程管理_数据库设计.md》v3.3 表 3/5/6/7。
 * <p>业务目标：页面一次拿到"这条流程改了什么、改成什么、谁办的、生效没有"，无需再联查多张表。
 */
@Schema(description = "管理后台 - 证件生命周期记录出参")
@Data
public class CertLifecycleRespVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "业务对象域 code")
    private String bizDomain;
    @Schema(description = "业务对象主键")
    private Long bizId;
    @Schema(description = "业务对象名称快照")
    private String refName;
    @Schema(description = "证书编号快照")
    private String refNo;
    @Schema(description = "归属企业")
    private Long entId;
    @Schema(description = "生命周期类型 code")
    private String lifecycleType;
    @Schema(description = "状态 code")
    private String status;
    @Schema(description = "申请单号")
    private String applyNo;
    @Schema(description = "变更归组号")
    private String changeGroupId;
    @Schema(description = "变更字段名")
    private String fieldName;
    @Schema(description = "变更前值")
    private String oldValue;
    @Schema(description = "变更后值")
    private String newValue;
    @Schema(description = "变更摘要")
    private String changeSummary;
    @Schema(description = "续期类型 code")
    private String renewalType;
    @Schema(description = "续期前有效期止")
    private LocalDate oldValidTo;
    @Schema(description = "续期后有效期起始")
    private LocalDate newValidFrom;
    @Schema(description = "续期后有效期止")
    private LocalDate newValidTo;
    @Schema(description = "续期后下次年检日")
    private LocalDate newNextReviewDate;
    @Schema(description = "新证书编号")
    private String newCertNo;
    @Schema(description = "批复文号")
    private String resultNo;
    @Schema(description = "注销吊销类型 code")
    private String revokeType;
    @Schema(description = "决定机关")
    private String revokeAuthority;
    @Schema(description = "决定事由")
    private String revokeReason;
    @Schema(description = "决定生效日期")
    private LocalDate revokeEffectiveAt;
    @Schema(description = "年检年度")
    private Integer reviewYear;
    @Schema(description = "年检日期")
    private LocalDate reviewDate;
    @Schema(description = "年检结论：1 通过 / 0 不通过")
    private Integer reviewResult;
    @Schema(description = "新文件 id")
    private Long newCertFileId;
    @Schema(description = "佐证材料文件 id 列表", example = "[1001,1002]")
    private List<Long> evidenceFileIds;
    @Schema(description = "申请人")
    private Long appliedBy;
    @Schema(description = "申请时间")
    private LocalDateTime appliedAt;
    @Schema(description = "办理人")
    private Long handledBy;
    @Schema(description = "办理时间")
    private LocalDateTime handledAt;
    @Schema(description = "生效时间（回写主档时间）")
    private LocalDateTime effectiveAt;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "乐观锁版本")
    private Integer version;
}
