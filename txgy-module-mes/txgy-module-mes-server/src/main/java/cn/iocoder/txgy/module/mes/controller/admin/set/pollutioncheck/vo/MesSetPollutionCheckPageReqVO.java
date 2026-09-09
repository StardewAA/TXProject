package cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - MES 安全环保检测-污染判定记录 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MesSetPollutionCheckPageReqVO extends PageParam {

    @Schema(description = "环节：PURCHASE_INBOUND/MATERIAL_ISSUE/WASTE_INTERMEDIATE/FINISHED_PRODUCT", example = "PURCHASE_INBOUND")
    private String stage;

    @Schema(description = "关联单号(采购入库/领料/产品入库等真实单据号)", example = "IR20260329000001")
    private String bizNo;

    @Schema(description = "批次号", example = "B20260903-001")
    private String batchNo;

    @Schema(description = "物料/产品名称(模糊)", example = "涂料")
    private String itemName;

    @Schema(description = "AI 初筛结果：CLEAN/POLLUTED/UNCERTAIN", example = "UNCERTAIN")
    private String aiResult;

    @Schema(description = "是否已复核：null=全部 true=已复核 false=待复核", example = "false")
    private Boolean reviewed;

}
