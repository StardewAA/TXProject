package cn.iocoder.txgy.module.cms.controller.admin.Quota.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 资质定额资料库 Response VO")
@Data
public class CmsQuotaRespVO {

    @Schema(description = "主键")
    private Long quotaId;
    @Schema(description = "资料名称")
    private String title;
    @Schema(description = "类别：1资质要求 2施工定额 3人工单价 4材料市场价 5规费标准 6报验流程 7资料模板")
    private Integer quotaType;
    @Schema(description = "适用地区")
    private String region;
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
    @Schema(description = "创建人")
    private String createBy;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
