package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证照台账详情/分页响应（自建 VO —— 开发规范 §3.1）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二；GB/T 36903—2018 §5.2 字段口径
 * （英文名/短名见 CertificateInfoDO 注释）；密级标注按《招投标模块开发规范》§4.8（列表/详情脱敏
 * L3 项 cert_no 由展示层按 §4.8 规则处理，后续里程碑挂 @Desensitize 注解）。
 */
@Data
public class CertificateRespVO {

    @Schema(description = "证照主键")
    private Long id;
    @Schema(description = "乐观锁版本（更新时回传）")
    private Integer version;
    @Schema(description = "证照名称（GB/T 36903 ZZMC）")
    private String certName;
    @Schema(description = "证书编号（GB/T 36903 ZZBH / 51号 ZSBH；L3 敏感）")
    private String certNo;
    @Schema(description = "证照大类 code")
    private String certType;
    @Schema(description = "证照类型代码（GB/T 36902 ZZLXDM）")
    private String certCategoryCode;
    @Schema(description = "资质等级")
    private String certLevel;
    @Schema(description = "许可/业务范围")
    private String certScope;
    @Schema(description = "持证主体（GB/T 36903 CZZT）")
    private String holderName;
    @Schema(description = "持证主体类别 code：ORG/PERSON/EXTERNAL")
    private String holderType;
    @Schema(description = "关联档案 ID")
    private Long holderId;
    @Schema(description = "证照颁发机构（GB/T 36903 ZZBFJG）")
    private String issuingAuthority;
    @Schema(description = "证照颁发机构代码（GB/T 36903 ZZBFJGDM）")
    private String issuingAuthorityCode;
    @Schema(description = "证照颁发日期（GB/T 36903 ZZBFRQ）")
    private LocalDate issueDate;
    @Schema(description = "证照有效期起始日期（GB/T 36903 ZZYXQQSRQ）")
    private LocalDate validFrom;
    @Schema(description = "证照有效期截止日期（GB/T 36903 ZZYXQJZRQ；null=长期）")
    private LocalDate validTo;
    @Schema(description = "台账状态 code（51号 ZZZT 对齐）")
    private String certStatus;
    @Schema(description = "验真状态 code")
    private String verificationStatus;
    @Schema(description = "最近验真时间")
    private LocalDateTime lastVerifiedAt;
    @Schema(description = "验真来源")
    private String verifyApiSource;
    @Schema(description = "官方核验链接（展示）")
    private String officialVerifyUrl;
    @Schema(description = "数据来源 code")
    private String sourceChannel;
    @Schema(description = "电子证照标识（GB/T 36904）")
    private String eCertIdent;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "建档时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
