package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.businessrel;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 售后转商机关联分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleBusinessRelPageReqVO extends PageParam {

    @Schema(description = "新商机编号", example = "9")
    private Long businessId;

    @Schema(description = "来源质保档案编号", example = "1")
    private Long warrantyId;

    @Schema(description = "客户编号", example = "12")
    private Long customerId;

    @Schema(description = "商机内容方向：1=复购 2=续签 3=扩项/新工程", example = "1")
    private Integer sourceType;

}
