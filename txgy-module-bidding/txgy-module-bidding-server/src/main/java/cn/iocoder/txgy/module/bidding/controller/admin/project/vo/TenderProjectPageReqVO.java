package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§4.4 分页入参
// [业务目标] 招标项目台账分页（编号/名称/所属项目/招标人/方式/状态）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 招标项目分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class TenderProjectPageReqVO extends PageParam {
    @Schema(description = "招标项目编号")
    private String tenderProjectCode;
    @Schema(description = "招标项目名称（模糊）")
    private String tenderProjectName;
    @Schema(description = "所属项目编号")
    private String projectCode;
    @Schema(description = "招标人名称（模糊）")
    private String tendererName;
    @Schema(description = "招标方式")
    private String tenderMode;
    @Schema(description = "内部管理态")
    private String state;
}
