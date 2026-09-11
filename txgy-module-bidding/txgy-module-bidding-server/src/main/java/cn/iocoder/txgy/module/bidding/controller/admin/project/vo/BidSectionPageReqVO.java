package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§4.4 分页入参
// [业务目标] 标段台账分页（编号/名称/所属招标项目/状态）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 标段（包）分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidSectionPageReqVO extends PageParam {
    @Schema(description = "标段（包）编号")
    private String bidSectionCode;
    @Schema(description = "标段（包）名称（模糊）")
    private String bidSectionName;
    @Schema(description = "所属招标项目编号")
    private String tenderProjectCode;
    @Schema(description = "内部管理态")
    private String state;
}
