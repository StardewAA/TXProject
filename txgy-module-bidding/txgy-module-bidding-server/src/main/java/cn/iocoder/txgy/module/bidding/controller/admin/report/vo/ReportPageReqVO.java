package cn.iocoder.txgy.module.bidding.controller.admin.report.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 定标书面报告分页入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportPageReqVO extends PageParam {

    @Schema(description = "招标项目编号", example = "I6101002026000001001")
    private String tenderProjectCode;

    @Schema(description = "标段编号", example = "I6101002026000001001001")
    private String bidSectionCode;
}
