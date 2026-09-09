package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveRoomDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 直播房间 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LiveRoomRespVO extends LiveRoomDO {

    @Schema(description = "当前观看人数", example = "12")
    private Integer viewerCount;

}
