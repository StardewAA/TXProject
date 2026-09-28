package cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 资格预审分页入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class PrequalPageReqVO extends PageParam {

    @Schema(description = "资格预审项目名称（模糊）")
    private String title;

    @Schema(description = "招标项目编号")
    private String tenderProjectCode;

    @Schema(description = "标段编号")
    private String bidSectionCode;

    @Schema(description = "状态（PrequalStatusEnum）")
    private String status;
}
