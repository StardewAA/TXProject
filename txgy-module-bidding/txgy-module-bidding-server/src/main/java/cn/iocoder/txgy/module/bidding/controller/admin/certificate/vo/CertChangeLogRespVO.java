package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 证照变更留痕 Response VO（契约：变更留痕读出口，2026-09-21 补）。
 *
 * <p>字段取舍：**不外发** tenant_id/deleted/updater 等内部列；
 * {@code recordHash}/{@code prevHash} **保留下发**——它们是哈希链的可验证凭证，
 * 前端做"留痕未被篡改"的展示需要；且二者本就是摘要值，不含敏感信息。
 */
@Schema(description = "管理后台 - 证照变更留痕 Response VO")
@Data
public class CertChangeLogRespVO {

    @Schema(description = "留痕主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "86")
    private Long id;

    @Schema(description = "证照主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "21")
    private Long certId;

    @Schema(description = "变更字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "cert_status")
    private String fieldName;

    @Schema(description = "变更前值（CREATE 类型为空）", example = "ACTIVE")
    private String oldValue;

    @Schema(description = "变更后值", example = "EXPIRED")
    private String newValue;

    @Schema(description = "变更类型 code：CREATE/UPDATE/STATUS_CHANGE/REVOKE", requiredMode = Schema.RequiredMode.REQUIRED, example = "STATUS_CHANGE")
    private String changeType;

    @Schema(description = "变更类型中文名（字典 change_type；解析失败为 null，前端回退显示 code）", example = "状态变更")
    private String changeTypeName;

    @Schema(description = "操作来源 code：MANUAL/IMPORT/OCR/API_SYNC/SYSTEM", example = "MANUAL")
    private String opSource;

    @Schema(description = "操作来源中文名（字典 op_source；解析失败为 null）", example = "手工操作")
    private String opSourceName;

    @Schema(description = "操作 IP（支持 IPv6）", example = "127.0.0.1")
    private String opIp;

    @Schema(description = "批次号（整批导入/回滚归组；同批次为一次操作）", example = "IMP-20260921-001")
    private String batchId;

    @Schema(description = "操作人编号（creator）", example = "1")
    private String operator;

    @Schema(description = "变更时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-20 11:30:01")
    private LocalDateTime createTime;

    @Schema(description = "本行哈希（SHA-256，v3.3 §13 字段拼接口径；用于留痕防篡改展示）")
    private String recordHash;

    @Schema(description = "哈希链前驱（链首为 ROOT；前端可据此串出链式时间轴）", example = "ROOT")
    private String prevHash;
}
