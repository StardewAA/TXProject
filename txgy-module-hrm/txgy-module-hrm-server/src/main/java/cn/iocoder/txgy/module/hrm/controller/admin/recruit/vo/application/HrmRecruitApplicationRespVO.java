package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 招聘应聘 Response VO")
@Data
public class HrmRecruitApplicationRespVO {
    private Long id;
    private Long candidateId;
    private Long postId;
    private Long resumeId;
    private Long channelId;
    private String status;
    private String statusName;
    private LocalDateTime statusUpdateTime;
    private String eliminateReason;
    private String withdrawReason;
    private Long employeeId;
    private LocalDateTime onboardTime;
    private LocalDateTime createTime;
}
