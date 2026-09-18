package cn.iocoder.txgy.module.cms.controller.admin.Quota.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 资质定额资料库创建/修改 Request VO")
@Data
public class CmsQuotaSaveReqVO {

    @Schema(description = "主键（更新时必传）")
    private Long quotaId;
    @NotNull(message = "资料名称不能为空")
    @Schema(description = "资料名称")
    private String title;
    @NotNull(message = "类别：1资质要求 2施工定额 3人工单价 4材料市场价 5规费标准 6报验流程 7资料模板不能为空")
    @Schema(description = "类别：1资质要求 2施工定额 3人工单价 4材料市场价 5规费标准 6报验流程 7资料模板")
    private Integer quotaType;
    @NotNull(message = "适用地区不能为空")
    @Schema(description = "适用地区")
    private String region;
    @NotNull(message = "发布时间不能为空")
    @Schema(description = "发布时间")
    private LocalDate publishDate;
    @Schema(description = "有效期至")
    private LocalDate validDate;
    @Schema(description = "文件附件ID")
    private Long fileId;
    @Schema(description = "说明备注")
    private String description;
    @Schema(description = "是否免费：0付费 1免费")
    private Integer isFree;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "审核备注（驳回原因）")
    private String auditRemark;
    @Schema(description = "发布状态")
    private Integer publishStatus;
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;
}
