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
    // [契约《后端变更请求-审批工作台平铺与项目代码过滤-2026-10-01》B5，2026-10-02 落地]
    // 项目代码 = 投资项目统一代码（国家 24 位关联码 INVEST_PROJECT_CODE）。此前后端无该入参，
    // 前端只能拉全量再本地过滤（实测 18 行可用、大数据量分页会失真）。
    @Schema(description = "投资项目统一代码（项目代码，前缀匹配）", example = "2101-610100-76-59900")
    private String investProjectCode;
    @Schema(description = "项目名称（模糊）")
    private String projectName;
    @Schema(description = "行政区划代码")
    private String regionCode;
    @Schema(description = "行业类别")
    private String industriesType;
    @Schema(description = "内部管理态：ACTIVE/CLOSED")
    private String state;
}
