package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 视频资源 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class VideoPageReqVO extends PageParam {

    @Schema(description = "关键词（标题模糊匹配）")
    private String keyword;

    @Schema(description = "分类 ID")
    private Long categoryId;

    @Schema(description = "状态：1 草稿 2 上线 3 下架 4 归档")
    private Integer status;

    @Schema(description = "转码状态：0 未转码 3 成功 4 失败")
    private Integer transcodeStatus;

    @Schema(description = "是否包含归档：默认 false 仅查非归档")
    private Boolean includeArchived;

}
