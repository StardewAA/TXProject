package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticketnote;

import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketNoteDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 售后工单流转记录 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleTicketNoteRespVO extends CrmAfterSaleTicketNoteDO {

    @Schema(description = "操作人昵称", example = "张三")
    private String operatorNickname;

}
