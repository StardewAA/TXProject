package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticketnote;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 售后工单流转记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleTicketNotePageReqVO extends PageParam {

    @Schema(description = "工单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long ticketId;

    @Schema(description = "操作类型：1=指派 2=状态变更 3=处理记录 4=转有偿 5=关闭", example = "2")
    private Integer operateType;

}
