package cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 招标文件下载名单分页入参（招标方查询，供澄清/异议定向通知）")
@Data
@EqualsAndHashCode(callSuper = true)
public class TenderDocDownloadPageReqVO extends PageParam {

    @Schema(description = "招标项目编号", example = "I6101002026000001001")
    private String tenderProjectCode;

    @Schema(description = "标段编号（空=项目级全部）", example = "I6101002026000001001001")
    private String bidSectionCode;
}
