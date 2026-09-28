package cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.crm.enums.common.CrmAuditStatusEnum;
import cn.iocoder.txgy.module.crm.enums.common.CrmSceneTypeEnum;
import cn.iocoder.txgy.module.crm.enums.quotation.CrmQuotationTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CRM 报价单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmQuotationPageReqVO extends PageParam {

    @Schema(description = "报价单号", example = "BJ008")
    private String no;

    @Schema(description = "报价名称", example = "防腐保温工程报价")
    private String name;

    @Schema(description = "客户编号", example = "18336")
    private Long customerId;

    @Schema(description = "商机编号", example = "10864")
    private Long businessId;

    @Schema(description = "场景类型", example = "1")
    @InEnum(CrmSceneTypeEnum.class)
    private Integer sceneType; // 场景类型，为 null 时则表示全部

    @Schema(description = "审批状态", example = "20")
    @InEnum(CrmAuditStatusEnum.class)
    private Integer auditStatus;

    @Schema(description = "报价类型：1=购销报价 2=采购报价", example = "1")
    @InEnum(CrmQuotationTypeEnum.class)
    private Integer quotationType;

}
