package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 验真历史行（管理端）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5；GB/T 36906—2018 §6.4。
 *
 * <p>为什么**不下发** request_snapshot / result_snapshot（L3 报文原文）：
 * 报文里含证照编号等可识别信息（对外须脱敏），而列表是高频读取面；L3 留痕的用途是**举证与审计**（需要时按主键查库取证），
 * 不是给界面展示。故这里只给一个"报文是否留痕"的布尔位，界面可据此提示"可申请调阅"，既不泄露也不丢可追溯性。
 *
 * <p>为什么带 certName：历史行的 cert_id 对人不可读，而列表按行批量回查证照名只需一次 in 查询
 * （不做 N+1），是"能看懂"和"能追溯"之间的最小代价。
 */
@Schema(description = "管理后台 - 验真历史行")
@Data
public class CertVerifyHistoryRespVO {

    @Schema(description = "验真历史主键（= 设计 verify_id）", example = "1")
    private Long id;

    @Schema(description = "被验真证照 ID", example = "1")
    private Long certId;

    @Schema(description = "证照名称（批量回查，便于人读）", example = "安全生产许可证")
    private String certName;

    @Schema(description = "验真渠道 code", example = "FOUR_PLATFORM")
    private String verifyChannel;

    @Schema(description = "验真结论 code", example = "MATCH")
    private String verifyResult;

    @Schema(description = "发起人 ID（含 SYSTEM）", example = "1024")
    private Long verifyBy;

    @Schema(description = "验真完成时间", example = "2026-09-13 10:00:00")
    private LocalDateTime verifiedAt;

    @Schema(description = "是否留有 L3 报文原文（内容不下发，需调阅请按主键查库取证）", example = "true")
    private Boolean snapshotAvailable;

}
