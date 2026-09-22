package cn.iocoder.txgy.module.trade.controller.app.checkout.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 平台结算正式化准备 Response VO")
@Data
public class AppPlatformCheckoutFormalizationPrepareRespVO {

    @Schema(description = "准备记录编号")
    private Long attemptId;
    @Schema(description = "平台结算单号")
    private String checkoutNo;
    @Schema(description = "服务端快照摘要")
    private String snapshotDigest;
    @Schema(description = "是否已满足正式创建条件；当前版本固定为 false")
    private Boolean ready;
    @Schema(description = "服务端阻断代码")
    private List<String> blockerCodes;
    @Schema(description = "逐项阻断原因")
    private List<String> blockerReasons;
    @Schema(description = "汇总阻断原因")
    private String reason;
}
