package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateVerifyStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - HRM 证照台账分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmCertificateLedgerPageReqVO extends PageParam {

    @Schema(description = "证照类型编号", example = "1")
    private Long typeId;

    @Schema(description = "员工编号", example = "119")
    private Long employeeId;

    @Schema(description = "证件名称（模糊匹配）", example = "高处")
    private String certificateName;

    @Schema(description = "核验状态：0 未核验，1 已核验，2 核验不通过", example = "0")
    @InEnum(value = HrmCertificateVerifyStatusEnum.class, message = "核验状态必须是 {value}")
    private Integer verifyStatus;

    @Schema(description = "到期状态：1 有效，2 临期，3 已过期", example = "2")
    private Integer expireStatus;

    @Schema(description = "临期判定天数（不传默认 90 天）", example = "90")
    private Integer warnDays;

}
