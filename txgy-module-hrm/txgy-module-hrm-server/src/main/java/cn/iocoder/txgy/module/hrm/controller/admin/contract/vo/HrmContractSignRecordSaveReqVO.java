package cn.iocoder.txgy.module.hrm.controller.admin.contract.vo;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignWayEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - HRM 合同签署记录保存 Request VO")
@Data
public class HrmContractSignRecordSaveReqVO {

    @Schema(description = "编号；为空表示新增", example = "1")
    private Long id;

    @Schema(description = "合同编号（hrm_employee_contract.id）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "8")
    @NotNull(message = "合同编号不能为空")
    private Long contractId;

    @Schema(description = "签署用工主体编号（hrm_company_profile.id）", example = "1")
    private Long signEmployerId;

    @Schema(description = "签署人（乙方签署人姓名）", example = "王建国")
    @Size(max = 64, message = "签署人不能超过 64 个字符")
    private String signPerson;

    @Schema(description = "签署方式：1 线下签署，2 电子签章", example = "1")
    @InEnum(value = HrmContractSignWayEnum.class, message = "签署方式必须是 {value}")
    private Integer signWay;

    @Schema(description = "签署时间")
    private LocalDateTime signTime;

    @Schema(description = "签署件附件地址列表")
    private List<String> fileUrls;

    @Schema(description = "状态：0 待签，1 已签，2 已作废", example = "1")
    @InEnum(value = HrmContractSignStatusEnum.class, message = "签署状态必须是 {value}")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
