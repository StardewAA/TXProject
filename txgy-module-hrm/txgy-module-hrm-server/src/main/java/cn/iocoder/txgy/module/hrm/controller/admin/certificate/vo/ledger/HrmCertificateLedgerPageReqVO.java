package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - HRM 证件台账分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmCertificateLedgerPageReqVO extends PageParam {

    @Schema(description = "证件名称，模糊匹配", example = "电工")
    private String name;

    @Schema(description = "证件编号，模糊匹配", example = "T4403")
    private String no;

    @Schema(description = "员工编号", example = "2048")
    private Long employeeId;

    @Schema(description = "证件类型编号", example = "1")
    private Long typeId;

    @Schema(description = "证件状态，参见 hrm_certificate_status", example = "2")
    private Integer status;

}
