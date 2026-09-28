package cn.iocoder.txgy.module.hrm.controller.admin.contract.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignWayEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - HRM 合同签署记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmContractSignRecordPageReqVO extends PageParam {

    @Schema(description = "合同编号", example = "8")
    private Long contractId;

    @Schema(description = "员工编号", example = "129")
    private Long employeeId;

    @Schema(description = "合同编号（模糊匹配）", example = "HT-OFF")
    private String contractNo;

    @Schema(description = "签署方式：1 线下签署，2 电子签章", example = "1")
    @InEnum(value = HrmContractSignWayEnum.class, message = "签署方式必须是 {value}")
    private Integer signWay;

    @Schema(description = "状态：0 待签，1 已签，2 已作废", example = "1")
    @InEnum(value = HrmContractSignStatusEnum.class, message = "签署状态必须是 {value}")
    private Integer status;

    @Schema(description = "签署时间范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] signTime;

}
