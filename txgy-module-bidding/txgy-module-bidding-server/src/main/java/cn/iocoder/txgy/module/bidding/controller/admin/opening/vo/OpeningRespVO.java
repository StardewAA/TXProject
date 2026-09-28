package cn.iocoder.txgy.module.bidding.controller.admin.opening.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 开标房间 Response")
@Data
public class OpeningRespVO {

    @Schema(description = "开标记录ID")
    private Long id;
    @Schema(description = "招标项目编号")
    private String tenderProjectCode;
    @Schema(description = "标段编号")
    private String bidSectionCode;
    @Schema(description = "开标时间")
    private LocalDateTime openingTime;
    @Schema(description = "开标状态：PENDING/DECRYPTING/DONE")
    private String openingStatus;
    @Schema(description = "房间状态：WAITING/DECRYPTING/OPENING/DONE")
    private String roomStatus;
    @Schema(description = "房间开放时间")
    private LocalDateTime roomOpenedTime;
    @Schema(description = "房间关闭时间")
    private LocalDateTime roomClosedTime;
    @Schema(description = "投标人数量")
    private Integer bidderCount;
    @Schema(description = "唱标清单 JSON")
    private String openList;
    @Schema(description = "开标主持人")
    private String operatorName;
}
