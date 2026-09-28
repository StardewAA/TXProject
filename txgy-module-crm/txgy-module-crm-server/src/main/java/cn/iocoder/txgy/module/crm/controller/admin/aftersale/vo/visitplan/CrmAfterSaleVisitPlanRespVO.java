package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan;

import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitPlanDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 售后回访计划 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleVisitPlanRespVO extends CrmAfterSaleVisitPlanDO {

    @Schema(description = "质保编号", example = "WB202609070001")
    private String warrantyNo;

    @Schema(description = "工程项目名称", example = "某储罐区防腐项目")
    private String projectName;

    @Schema(description = "客户名称", example = "某某科技有限公司")
    private String customerName;

    @Schema(description = "指定处理人昵称")
    private String assigneeNickname;

}
