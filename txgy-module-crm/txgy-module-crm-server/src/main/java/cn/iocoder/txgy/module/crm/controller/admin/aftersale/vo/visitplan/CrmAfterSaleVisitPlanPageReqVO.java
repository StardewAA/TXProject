package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 售后回访计划分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleVisitPlanPageReqVO extends PageParam {

    @Schema(description = "计划名称", example = "质保期内季度回访")
    private String name;

    @Schema(description = "关联质保档案编号", example = "1")
    private Long warrantyId;

    @Schema(description = "计划类型：1=手动 2=自动", example = "2")
    private Integer planType;

    @Schema(description = "计划状态：1=启用 2=停用 3=已完成", example = "1")
    private Integer status;

    @Schema(description = "任务处理人类型：1=售后人员 2=业务员", example = "1")
    private Integer assigneeType;

}
