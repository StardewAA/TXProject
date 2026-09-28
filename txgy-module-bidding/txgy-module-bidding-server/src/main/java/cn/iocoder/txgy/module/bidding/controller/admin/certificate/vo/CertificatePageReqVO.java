package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 证照台账分页查询入参（管理端；租户隔离由平台拦截器实施）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二（台账字段口径）；
 * 《招投标模块开发规范》§4.2/§4.4（分页 PageParam、查询走 Mapper 条件拼装，不做 LIKE 全文）。
 */
@Schema(description = "管理后台 - 证照台账分页查询入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertificatePageReqVO extends PageParam {

    @Schema(description = "证照名称（模糊）", example = "建筑业企业资质证书")
    private String certName;

    @Schema(description = "证书编号（精确）", example = "D123456789")
    private String certNo;

    @Schema(description = "证照类型 code（字典 cert_type，如 ADMIN_LICENSE 营业执照）", example = "ADMIN_LICENSE")
    private String certType;

    @Schema(description = "证照大类 code（字典 cert_category：IDENTITY 身份类 / QUALIFICATION 资质类 / "
            + "PERFORMANCE 业绩类 / CREDIT 资信类）；由后端按字典 cert_type_category 展开为 certType in (...)，"
            + "与 certType 同传时形成交集", example = "IDENTITY")
    private String certCategory;

    @Schema(description = "台账状态 code 多选（ACTIVE/EXPIRING_SOON/EXPIRED/SUSPENDED/REVOKED/CANCELLED）；与 certStatus 同传时以本字段为准（Q3：原只支持单个 code）", example = "ACTIVE")
    private List<String> certStatuses;

    @Schema(description = "台账状态 code 单选（兼容旧口径；多选字段为空时生效）", example = "ACTIVE")
    private String certStatus;

    @Schema(description = "持证主体（模糊）", example = "××建设")
    private String holderName;

    @Schema(description = "验真状态 code 多选（UNVERIFIED/VERIFYING/VERIFIED/INVALID）；与 verificationStatus 同传时以本字段为准", example = "UNVERIFIED")
    private List<String> verificationStatuses;

    @Schema(description = "验真状态 code 单选（兼容旧口径；多选字段为空时生效）", example = "UNVERIFIED")
    private String verificationStatus;
}
