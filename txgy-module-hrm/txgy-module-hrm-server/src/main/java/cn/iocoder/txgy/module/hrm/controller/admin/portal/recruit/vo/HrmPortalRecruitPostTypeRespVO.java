package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "员工端 - 招聘职位类型 Response VO")
@Data
public class HrmPortalRecruitPostTypeRespVO {
    private Long id;
    private String name;
    private Long parentId;
    private Integer sort;
}
