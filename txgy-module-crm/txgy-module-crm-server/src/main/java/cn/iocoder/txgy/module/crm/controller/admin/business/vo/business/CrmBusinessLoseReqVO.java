package cn.iocoder.txgy.module.crm.controller.admin.business.vo.business;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessLostReasonEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - CRM 商机输单 Request VO
 *
 * <p>点击「输单」按钮后弹出，丢单原因必填、丢单备注选填；
 * 提交后商机状态组=已丢单、预估赢单率=0，并锁定关键字段。
 *
 * @author 防腐保温项目组
 */
@Schema(description = "管理后台 - CRM 商机输单 Request VO")
@Data
public class CrmBusinessLoseReqVO {

    @Schema(description = "商机编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "32129")
    @NotNull(message = "商机编号不能为空")
    private Long id;

    @Schema(description = "丢单原因：1客户预算不足/预算取消 2客户选择竞品 3项目无限期搁置 4客户内部决策人变更 5客户需求重大变更 6其他",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "丢单原因不能为空")
    @InEnum(CrmBusinessLostReasonEnum.class)
    private Integer lostReason;

    @Schema(description = "丢单备注（选填；丢单原因选「其他」时建议填写）", example = "客户已选定其他供应商")
    @Size(max = 500, message = "丢单备注长度不能超过 500 个字符")
    private String lostRemark;

}
