package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - HRM 证照类型分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmCertificateTypePageReqVO extends PageParam {

    @Schema(description = "证照类型名称（模糊匹配）", example = "高处")
    private String name;

    @Schema(description = "是否特种作业", example = "true")
    private Boolean specialOperation;

    @Schema(description = "是否必持", example = "true")
    private Boolean mandatory;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

}
