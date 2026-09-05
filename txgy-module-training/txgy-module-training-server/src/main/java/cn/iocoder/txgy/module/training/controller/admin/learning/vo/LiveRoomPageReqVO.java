package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 直播房间分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LiveRoomPageReqVO extends PageParam {

    @Schema(description = "直播标题（模糊）", example = "安全")
    private String title;

    @Schema(description = "状态：0 未开始 1 直播中 2 已结束", example = "1")
    private Integer status;

}
