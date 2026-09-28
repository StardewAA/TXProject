package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty;

import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 售后质保档案 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleWarrantyRespVO extends CrmAfterSaleWarrantyDO {

    @Schema(description = "合同编号", example = "HT2026001")
    private String contractNo;

    @Schema(description = "合同名称", example = "某某防腐工程合同")
    private String contractName;

    @Schema(description = "客户名称", example = "某某科技有限公司")
    private String customerName;

    @Schema(description = "质保剩余天数；负数表示已到期")
    private Long remainDays;

}
