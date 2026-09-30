package cn.iocoder.txgy.module.hrm.controller.admin.policy.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - HRM 政策分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmPolicyPageReqVO extends PageParam {

    @Schema(description = "政策名称（模糊匹配）")
    private String name;

    @Schema(description = "类别")
    private String category;

    @Schema(description = "适用范围")
    private String scope;

    @Schema(description = "状态：0 启用 1 停用")
    private Integer status;
}
