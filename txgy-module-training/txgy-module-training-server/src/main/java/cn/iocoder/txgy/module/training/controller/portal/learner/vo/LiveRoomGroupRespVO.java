package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "学员端 - 直播大厅（按状态分组）Response VO")
@Data
public class LiveRoomGroupRespVO {

    @Schema(description = "未开始直播")
    private List<PortalLiveRoomVO> upcoming;

    @Schema(description = "直播中")
    private List<PortalLiveRoomVO> live;

    @Schema(description = "已结束（含回放）")
    private List<PortalLiveRoomVO> ended;

}
