package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "员工端 - 我的招聘应聘分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmPortalRecruitApplicationPageReqVO extends PageParam {
    private String status;
}
