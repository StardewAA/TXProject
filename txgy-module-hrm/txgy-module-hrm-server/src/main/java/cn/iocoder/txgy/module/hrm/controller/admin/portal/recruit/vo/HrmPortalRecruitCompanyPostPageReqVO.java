package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户端 - 企业在招职位分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmPortalRecruitCompanyPostPageReqVO extends PageParam {

    @NotNull
    private Long tenantId;
}
