package cn.iocoder.txgy.module.cms.controller.admin.Quota.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 资质定额资料库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsQuotaPageReqVO extends PageParam {

    @Schema(description = "资料名称")
    private String title;
    @Schema(description = "类别：1资质要求 2施工定额 3人工单价 4材料市场价 5规费标准 6报验流程 7资料模板")
    private Integer quotaType;
    @Schema(description = "适用地区")
    private String region;
    @Schema(description = "是否免费：0付费 1免费")
    private Integer isFree;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "发布状态")
    private Integer publishStatus;
}
