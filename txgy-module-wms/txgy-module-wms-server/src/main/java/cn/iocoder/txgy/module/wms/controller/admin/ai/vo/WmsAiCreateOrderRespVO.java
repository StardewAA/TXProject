package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - WMS AI 确认建单 Response VO
 *
 * @author 天信管业
 */
@Schema(description = "管理后台 - WMS AI 确认建单 Response VO")
@Data
public class WmsAiCreateOrderRespVO {

    @Schema(description = "单据编号", example = "RK202609180001")
    private String orderNo;

    @Schema(description = "单据主键编号", example = "3101015")
    private Long orderId;

    @Schema(description = "给用户的自然语言回复", example = "已生成入库单 RK202609180001（草稿）")
    private String reply;

}
