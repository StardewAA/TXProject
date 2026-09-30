package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - CA 签章统计 Response（演示仪表卡）")
@Data
public class CaSealStatsRespVO {

    @Schema(description = "签章记录总数（直接入库）")
    private Long total;

    @Schema(description = "验签通过数（PASS）")
    private Long pass;

    @Schema(description = "验签不通过数（FAIL）")
    private Long fail;

    @Schema(description = "未验签数（UNVERIFIED）")
    private Long unverified;
}
