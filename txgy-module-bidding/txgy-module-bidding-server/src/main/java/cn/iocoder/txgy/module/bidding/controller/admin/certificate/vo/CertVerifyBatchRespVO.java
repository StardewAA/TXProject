package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 批量验真响应（五页改造契约 Q2，2026-09-28）。
 *
 * <p>逐条调用单验真（幂等窗口/回执落库复用），行级回执；单条失败不影响其余。
 */
@Schema(description = "管理后台 - 批量验真 Response")
@Data
public class CertVerifyBatchRespVO {

    @Schema(description = "行级回执列表")
    private List<CertVerifyBatchItemRespVO> results;

    @Schema(description = "行级回执列表（= results 的别名；前端契约字段名 items）")
    private List<CertVerifyBatchItemRespVO> items;

    @Schema(description = "成功行数")
    private Integer successCount;

    @Schema(description = "失败行数")
    private Integer failureCount;
}
