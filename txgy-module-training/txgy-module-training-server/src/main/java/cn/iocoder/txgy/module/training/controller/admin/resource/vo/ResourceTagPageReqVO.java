package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 资源标签 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceTagPageReqVO extends PageParam {

    @Schema(description = "标签名模糊匹配")
    private String name;

    @Schema(description = "标签维度：work_type/course/safety/process")
    private String dimension;

}
