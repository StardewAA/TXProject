package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§4.4（分页查询入参规范）
// [业务目标] 项目台账分页检索（编号/名称/地区/行业/状态）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPageReqVO extends PageParam {

    @Schema(description = "项目编号（精确/前缀）")
    private String projectCode;
    @Schema(description = "项目名称（模糊）")
    private String projectName;
    @Schema(description = "行政区划代码")
    private String regionCode;
    @Schema(description = "行业类别")
    private String industriesType;
    @Schema(description = "内部管理态：ACTIVE/CLOSED")
    private String state;
}
