package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "员工端 - 招聘沟通会话 Response VO")
@Data
public class HrmPortalRecruitChatConversationRespVO {
    private Long id;
    private Long postId;
    private String postName;
    private Integer areaId;
    private String areaName;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private Integer salaryUnit;
    private Long hrUserId;
    private Long candidateUserId;
    private Long candidateId;
    private Long peerUserId;
    private String peerUserName;
    private String peerUserAvatar;
    private String candidateName;
    private Integer candidateSex;
    private Integer candidateAge;
    private Integer candidateWorkTime;
    private Integer candidateEducation;
    private String candidateEducationName;
    private String candidateGraduateSchool;
    private Boolean hrSide;
    /** 会话状态：0 待发起，1 等待回复，2 招聘方已回复，3 沟通失败 */
    private Integer status;
    private Boolean applyQualified;
    /** 正式投递状态：待投递、已投递。 */
    private String applicationStatus;
    private Long applicationId;
    private LocalDateTime hrReplyTime;
    private LocalDateTime endTime;
    private String endReason;
    private String lastContent;
    private Long lastSenderUserId;
    private LocalDateTime lastTime;
    private Integer unreadCount;
    /** AI 应聘匹配分 0-100，该会话尚未分析过时为 null */
    private Integer aiMatchScore;
    /** AI 匹配分析的任务编号，供招聘方查看完整结论 */
    private Long aiTaskId;
}
