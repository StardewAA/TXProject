package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "员工端 - 招聘应聘 Response VO")
@Data
public class HrmPortalRecruitApplicationRespVO {
    private Long id;
    private Long postId;
    private String postName;
    private String status;
    private String statusName;
    private LocalDateTime statusUpdateTime;
    private LocalDateTime createTime;
    private String candidateName;
    private String mobileMasked;
    private String email;
    private String resumeFileName;
    private String resumeFileUrl;
    /** 投递岗位负责人的系统用户编号；仅对当前申请人返回。 */
    private Long ownerUserId;
    /** 投递岗位负责人的昵称。 */
    private String ownerUserName;
    /** 投递岗位负责人的头像。 */
    private String ownerUserAvatar;
    private List<Interview> interviews;
    private Offer offer;

    @Data
    public static class Interview {
        private Long id;
        private Integer type;
        private Integer stageNumber;
        private LocalDateTime interviewTime;
        private String address;
        private String remark;
        private Integer result;
    }

    @Data
    public static class Offer {
        private Long id;
        private String offerNo;
        private String approvalStatus;
        private BigDecimal salaryAmount;
        private Integer salaryUnit;
        private LocalDate plannedOnboardDate;
        private LocalDate validUntil;
        private LocalDateTime sentTime;
        private LocalDateTime confirmTime;
    }
}
