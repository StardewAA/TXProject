package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证照变更留痕分页 Request VO（契约：变更留痕读出口，2026-09-21 补）。
 *
 * <p>为什么要有这个接口：cert_change_logs 表与写入逻辑一直存在（建档/更新/状态流转都留痕、
 * 含 v3.3 §13 哈希链），但**此前没有任何查询接口**——前端无法回答"这条证照改过什么"。
 */
@Schema(description = "管理后台 - 证照变更留痕分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertChangeLogPageReqVO extends PageParam {

    // 必传且必须真校验：@Schema 的 requiredMode 只影响文档，不加 @NotNull 时
    // 不传 certId 会退化成"查全部租户留痕"，既不安全也不是调用方本意（2026-09-21 实测发现）。
    @Schema(description = "证照主键（必传：留痕是某条证照的历史）", requiredMode = Schema.RequiredMode.REQUIRED, example = "21")
    @NotNull(message = "证照主键不能为空")
    private Long certId;

    @Schema(description = "变更类型 code：CREATE/UPDATE/STATUS_CHANGE/REVOKE", example = "UPDATE")
    private String changeType;

    @Schema(description = "变更字段名（按字段追某一项的历史，如 cert_status/valid_to）", example = "cert_status")
    private String fieldName;

    @Schema(description = "批次号（整批导入/回滚归组；按批次查同一次操作的全部留痕）", example = "IMP-20260921-001")
    private String batchId;
}
