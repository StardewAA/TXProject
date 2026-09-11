package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket;

import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 售后问题工单 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleTicketRespVO extends CrmAfterSaleTicketDO {

    @Schema(description = "客户名称", example = "某某科技有限公司")
    private String customerName;

    @Schema(description = "合同编号", example = "HT2026001")
    private String contractNo;

    @Schema(description = "质保编号", example = "WB202609070001")
    private String warrantyNo;

    @Schema(description = "处理人昵称", example = "李工")
    private String assigneeNickname;

    @Schema(description = "报单人昵称", example = "张三")
    private String creatorNickname;

}
