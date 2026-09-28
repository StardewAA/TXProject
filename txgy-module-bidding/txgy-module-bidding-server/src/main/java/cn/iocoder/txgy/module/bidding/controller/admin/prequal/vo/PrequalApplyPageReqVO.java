package cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 资格预审申请分页入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class PrequalApplyPageReqVO extends PageParam {

    // [契约]《后端变更请求-投标与资格预审多文件化-2026-10-02.md》R6：标段详情页按标段聚合该标段所有预审申请
    @Schema(description = "标段编号（精确匹配；契约多文件化 R6）")
    private String bidSectionCode;

    @Schema(description = "资格预审ID")
    private Long prequalId;

    @Schema(description = "投标主体ID")
    private Long subjectId;

    @Schema(description = "状态（PrequalApplyStatusEnum）")
    private String status;
}
