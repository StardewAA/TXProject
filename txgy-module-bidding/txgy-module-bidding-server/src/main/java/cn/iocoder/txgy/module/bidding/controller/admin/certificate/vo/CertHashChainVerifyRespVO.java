package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 证照变更留痕哈希链回验结果（v3.3 §13 哈希链 / v3.4 §18.3）。
 *
 * <p>用途：把「留痕未被篡改」从**口号**变成**可执行能力** —— 逐行重算 record_hash 并核对 prev_hash 链接，
 * 断链即指出第一处不一致的行 id 与期望/实际值（便于定位被改动的那一行）。
 */
@Schema(description = "管理后台 - 证照变更留痕哈希链回验结果")
@Data
public class CertHashChainVerifyRespVO {

    @Schema(description = "整链是否自洽", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean ok;

    @Schema(description = "本次回验行数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer checked;

    @Schema(description = "首处断链的行 id（ok=true 时为 null）")
    private Long brokenAtId;

    @Schema(description = "断链原因：PREV_HASH_MISMATCH / RECORD_HASH_MISMATCH")
    private String reason;

    @Schema(description = "期望值（重算所得或上一行 record_hash）")
    private String expected;

    @Schema(description = "实际值（该行存储的）")
    private String actual;
}