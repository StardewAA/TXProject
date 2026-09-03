package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - HRM 招聘应聘分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitApplicationPageReqVO extends PageParam {
    private Long candidateId;
    private Long postId;
    private Long channelId;
    private String status;
    private String keyword;
}
