package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationStatusReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplyReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplicationPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationLogDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationLogMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.candidate.HrmRecruitCandidateMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitInterviewService;
import cn.iocoder.txgy.module.hrm.service.recruit.chat.HrmRecruitChatService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.candidate.HrmRecruitCandidateSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.*;

@Service
@Validated
public class HrmRecruitApplicationServiceImpl implements HrmRecruitApplicationService {

    @Resource
    private HrmRecruitApplicationMapper applicationMapper;
    @Resource
    private HrmRecruitApplicationLogMapper applicationLogMapper;
    @Resource
    private HrmRecruitCandidateMapper candidateMapper;
    @Resource
    private HrmRecruitCandidateService candidateService;
    @Resource
    private HrmRecruitPostService postService;
    @Resource
    private HrmRecruitResumeService resumeService;
    @Resource
    private HrmPersonalResumeService personalResumeService;
    @Resource
    private HrmRecruitChatService chatService;
    @Resource
    @Lazy
    private HrmRecruitInterviewService interviewService;
    @Resource
    @Lazy
    private HrmRecruitOfferService offerService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createApplication(HrmRecruitApplicationCreateReqVO reqVO) {
        candidateService.validateRecruitCandidateExists(reqVO.getCandidateId());
        postService.validateRecruitPostPublished(reqVO.getPostId());
        HrmRecruitApplicationDO processing = applicationMapper.selectProcessingByCandidateAndPost(
                reqVO.getCandidateId(), reqVO.getPostId(), HrmRecruitApplicationStatusEnum.PROCESSING_STATUSES);
        if (processing != null) {
            throw exception(RECRUIT_APPLICATION_DUPLICATE);
        }
        HrmRecruitApplicationDO application = BeanUtils.toBean(reqVO, HrmRecruitApplicationDO.class)
                .setStatus(HrmRecruitApplicationStatusEnum.SUBMITTED.getStatus())
                .setStatusUpdateTime(LocalDateTime.now()).setVersion(0);
        application.setTenantId(TenantContextHolder.getTenantId());
        applicationMapper.insert(application);
        writeLog(application, null, application.getStatus(), "CREATE", null);
        candidateService.syncRecruitCandidateProjection(application.getCandidateId());
        return application.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createApplicationFromRecruitChat(Long applicantUserId, Long candidateId, Long postId, Long resumeId) {
        candidateService.validateRecruitCandidateExists(candidateId);
        postService.validateRecruitPostExists(postId);
        HrmRecruitApplicationDO processing = applicationMapper.selectProcessingByCandidateAndPost(
                candidateId, postId, HrmRecruitApplicationStatusEnum.PROCESSING_STATUSES);
        if (processing != null) {
            return processing.getId();
        }
        HrmRecruitApplicationDO application = new HrmRecruitApplicationDO()
                .setApplicantUserId(applicantUserId)
                .setCandidateId(candidateId)
                .setPostId(postId)
                .setResumeId(resumeId)
                .setStatus(HrmRecruitApplicationStatusEnum.SUBMITTED.getStatus())
                .setStatusUpdateTime(LocalDateTime.now())
                .setVersion(0);
        application.setTenantId(TenantContextHolder.getTenantId());
        applicationMapper.insert(application);
        writeLog(application, null, application.getStatus(), "CHAT_CONVERT_CANDIDATE", "招聘沟通手动转为候选人");
        candidateService.syncRecruitCandidateProjection(candidateId);
        return application.getId();
    }

    @Override
    public PageResult<HrmRecruitApplicationDO> getApplicationPage(HrmRecruitApplicationPageReqVO reqVO) {
        offerService.expireOverdueOffers(null);
        return applicationMapper.selectPage(reqVO);
    }

    @Override
    public HrmRecruitApplicationDO getApplication(Long id) {
        return applicationMapper.selectById(id);
    }

    @Override
    public HrmRecruitApplicationDO getLatestRecruitApplicationByCandidateAndPost(Long candidateId, Long postId) {
        if (candidateId == null || postId == null) {
            return null;
        }
        return applicationMapper.selectLatestByCandidateAndPost(candidateId, postId);
    }

    @Override
    public HrmRecruitApplicationDO getCurrentRecruitApplicationByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return null;
        }
        return applicationMapper.selectCurrentByCandidateId(candidateId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startScreening(HrmRecruitApplicationStatusReqVO reqVO) {
        transition(reqVO, HrmRecruitApplicationStatusEnum.SCREENING, "START_SCREENING", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void passScreening(HrmRecruitApplicationStatusReqVO reqVO) {
        transition(reqVO, HrmRecruitApplicationStatusEnum.SCREENING_PASSED, "SCREENING_PASS", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectResume(HrmRecruitApplicationStatusReqVO reqVO) {
        transition(reqVO, HrmRecruitApplicationStatusEnum.RESUME_REJECTED, "RESUME_REJECTED", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enterInterview(HrmRecruitApplicationStatusReqVO reqVO) {
        HrmRecruitApplicationDO application = getApplication(reqVO.getId());
        if (application == null) {
            throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        }
        if (HrmRecruitApplicationStatusEnum.PENDING_INTERVIEW.getStatus().equals(application.getStatus())
                || HrmRecruitApplicationStatusEnum.INTERVIEW_SCHEDULED.getStatus().equals(application.getStatus())
                || HrmRecruitApplicationStatusEnum.INTERVIEWING.getStatus().equals(application.getStatus())) {
            return;
        }
        transition(reqVO, HrmRecruitApplicationStatusEnum.PENDING_INTERVIEW, "PENDING_INTERVIEW", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void skipInterview(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.PASSED, "INTERVIEW_SKIPPED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInterviewScheduled(Long id) {
        HrmRecruitApplicationDO application = getApplication(id);
        if (application != null
                && HrmRecruitApplicationStatusEnum.INTERVIEW_SCHEDULED.getStatus().equals(application.getStatus())) {
            return;
        }
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.INTERVIEW_SCHEDULED, "INTERVIEW_SCHEDULED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInterviewStarted(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.INTERVIEWING, "INTERVIEW_STARTED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInterviewEvaluating(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.INTERVIEW_EVALUATING, "INTERVIEW_EVALUATING", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInterviewCanceled(Long id, String reason) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id).setReason(reason),
                HrmRecruitApplicationStatusEnum.INTERVIEW_CANCELED, "INTERVIEW_CANCELED", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInterviewAbsent(Long id, String reason) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id).setReason(reason),
                HrmRecruitApplicationStatusEnum.INTERVIEW_ABSENT, "INTERVIEW_ABSENT", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInterviewRejected(Long id, String reason) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id).setReason(reason),
                HrmRecruitApplicationStatusEnum.INTERVIEW_REJECTED, "INTERVIEW_REJECTED", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNextInterview(Long id, String reason) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id).setReason(reason),
                HrmRecruitApplicationStatusEnum.NEXT_INTERVIEW, "NEXT_INTERVIEW", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFinalInterviewPassed(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.FINAL_INTERVIEW_PASSED, "FINAL_INTERVIEW_PASSED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPendingOffer(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.PENDING_OFFER, "PENDING_OFFER", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInterviewPassed(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.PASSED, "INTERVIEW_PASS", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void eliminateFromInterview(Long id, String reason) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id).setReason(reason),
                HrmRecruitApplicationStatusEnum.ELIMINATED, "INTERVIEW_NOT_PASS", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOfferSent(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.OFFER_SENT, "OFFER_SENT", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOfferApproving(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.OFFER_APPROVING, "OFFER_SUBMIT", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOfferRejected(Long id) {
        markOfferRejected(id, "Offer审批未通过");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOfferRejected(Long id, String reason) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id)
                        .setReason(StrUtil.blankToDefault(reason, "Offer审批未通过")),
                HrmRecruitApplicationStatusEnum.OFFER_REJECTED, "OFFER_APPROVAL_REJECTED", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOfferWithdrawn(Long id, String reason) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id)
                        .setReason(StrUtil.blankToDefault(reason, "招聘方撤回 Offer")),
                HrmRecruitApplicationStatusEnum.OFFER_WITHDRAWN, "OFFER_WITHDRAWN", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOfferExpired(Long id, String reason) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id).setReason(reason),
                HrmRecruitApplicationStatusEnum.OFFER_EXPIRED, "OFFER_EXPIRED", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOfferCandidateRejected(Long id, String reason) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id)
                        .setReason(StrUtil.blankToDefault(reason, "求职者拒绝 Offer")),
                HrmRecruitApplicationStatusEnum.OFFER_CANDIDATE_REJECTED,
                "OFFER_CANDIDATE_REJECTED", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPendingOnboard(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.PENDING_ONBOARD, "OFFER_CONFIRMED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkEmployeeForOnboard(Long id, Long employeeId) {
        HrmRecruitApplicationDO application = applicationMapper.selectByIdForUpdate(id);
        if (application == null) {
            throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        }
        if (!HrmRecruitApplicationStatusEnum.PENDING_ONBOARD.getStatus().equals(application.getStatus())
                || employeeId == null) {
            throw exception(RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID,
                    HrmRecruitApplicationStatusEnum.PENDING_ONBOARD.getName());
        }
        HrmRecruitApplicationDO update = new HrmRecruitApplicationDO().setId(id).setEmployeeId(employeeId)
                .setVersion((application.getVersion() == null ? 0 : application.getVersion()) + 1);
        if (applicationMapper.updateStatusWithVersion(id, application.getVersion(), update) == 0) {
            throw exception(RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID,
                    HrmRecruitApplicationStatusEnum.PENDING_ONBOARD.getName());
        }
        writeLog(application, application.getStatus(), application.getStatus(),
                "EMPLOYEE_CREATED_PENDING_ONBOARD", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOnboarded(Long id, Long employeeId) {
        HrmRecruitApplicationDO application = applicationMapper.selectByIdForUpdate(id);
        if (application == null) throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        if (!HrmRecruitApplicationStatusEnum.PENDING_ONBOARD.getStatus().equals(application.getStatus())) {
            throw exception(RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID, HrmRecruitApplicationStatusEnum.ONBOARDED.getName());
        }
        LocalDateTime onboardTime = LocalDateTime.now();
        HrmRecruitApplicationDO update = new HrmRecruitApplicationDO().setId(id)
                .setEmployeeId(employeeId).setStatus(HrmRecruitApplicationStatusEnum.ONBOARDED.getStatus())
                .setStatusUpdateTime(onboardTime).setOnboardTime(onboardTime)
                .setVersion((application.getVersion() == null ? 0 : application.getVersion()) + 1);
        if (applicationMapper.updateStatusWithVersion(id, application.getVersion(), update) == 0) {
            throw exception(RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID, HrmRecruitApplicationStatusEnum.ONBOARDED.getName());
        }
        writeLog(application, application.getStatus(), HrmRecruitApplicationStatusEnum.ONBOARDED.getStatus(), "ONBOARDED", null);
        candidateService.syncRecruitCandidateProjection(application.getCandidateId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void eliminate(HrmRecruitApplicationStatusReqVO reqVO) {
        transition(reqVO, HrmRecruitApplicationStatusEnum.ELIMINATED, "ELIMINATE", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(HrmRecruitApplicationStatusReqVO reqVO) {
        transition(reqVO, HrmRecruitApplicationStatusEnum.WITHDRAWN, "WITHDRAW", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPortalApplication(Long applicantUserId, HrmPortalRecruitApplyReqVO reqVO) {
        // 岗位是公开资源，先跨租户读取并确定其所属企业；后续所有投递数据都写入该企业租户。
        HrmRecruitPostDO post = postService.getPortalRecruitPostPublished(reqVO.getPostId());
        Long targetTenantId = post.getTenantId();
        if (targetTenantId == null) {
            throw exception(RECRUIT_POST_NOT_EXISTS);
        }
        AtomicReference<Long> applicationId = new AtomicReference<>();
        TenantUtils.execute(targetTenantId,
                () -> applicationId.set(createPortalApplicationInTenant(applicantUserId, reqVO, targetTenantId)));
        return applicationId.get();
    }

    private Long createPortalApplicationInTenant(Long applicantUserId, HrmPortalRecruitApplyReqVO reqVO,
                                                 Long targetTenantId) {
        postService.validateRecruitPostPublished(reqVO.getPostId());
        if (!chatService.hasApplyQualification(applicantUserId, reqVO.getPostId())) {
            throw exception(RECRUIT_CHAT_APPLY_NOT_QUALIFIED);
        }
        HrmRecruitApplicationDO processing = applicationMapper.selectProcessingByApplicantAndPost(
                applicantUserId, reqVO.getPostId(), HrmRecruitApplicationStatusEnum.PROCESSING_STATUSES);
        if (processing != null) {
            throw exception(RECRUIT_APPLICATION_DUPLICATE);
        }
        HrmRecruitCandidateDO candidate = candidateService.getRecruitCandidateByMobile(reqVO.getMobile());
        if (candidate == null) {
            candidate = new HrmRecruitCandidateDO();
            Long candidateId = candidateService.createRecruitCandidate(new HrmRecruitCandidateSaveReqVO()
                    .setName(reqVO.getName()).setMobile(reqVO.getMobile()).setSex(reqVO.getSex())
                    .setAge(reqVO.getAge()).setEmail(reqVO.getEmail()).setPostId(reqVO.getPostId())
                    .setWorkTime(reqVO.getWorkTime()).setEducation(reqVO.getEducation())
                    .setGraduateSchool(reqVO.getGraduateSchool()).setLatestWorkPlace(reqVO.getLatestWorkPlace()));
            candidate = candidateService.validateRecruitCandidateExists(candidateId);
        } else {
            HrmRecruitCandidateDO lockedCandidate = candidateMapper.selectByIdForUpdate(candidate.getId());
            if (lockedCandidate != null) {
                candidate = lockedCandidate;
            }
        }
        processing = applicationMapper.selectProcessingByCandidateAndPost(
                candidate.getId(), reqVO.getPostId(), HrmRecruitApplicationStatusEnum.PROCESSING_STATUSES);
        if (processing != null) {
            throw exception(RECRUIT_APPLICATION_DUPLICATE);
        }
        String resumeFileName = reqVO.getResumeFileName();
        String resumeFileUrl = reqVO.getResumeFileUrl();
        String resumeRemark = reqVO.getResumeRemark();
        if (reqVO.getResumeId() != null) {
            HrmPersonalResumeDO personalResume = personalResumeService.getResume(applicantUserId, reqVO.getResumeId());
            if (personalResume == null || StrUtil.isBlank(personalResume.getFileUrl())) {
                throw exception(RECRUIT_RESUME_NOT_EXISTS);
            }
            resumeFileName = StrUtil.blankToDefault(personalResume.getFileName(), personalResume.getTitle());
            resumeFileUrl = personalResume.getFileUrl();
            resumeRemark = personalResume.getRemark();
        }
        Long resumeId = resumeService.createRecruitResume(new HrmRecruitResumeSaveReqVO()
                .setCandidateId(candidate.getId()).setFileName(resumeFileName)
                .setFileUrl(resumeFileUrl).setRemark(resumeRemark));
        HrmRecruitApplicationDO application = new HrmRecruitApplicationDO()
                .setApplicantUserId(applicantUserId).setCandidateId(candidate.getId())
                .setPostId(reqVO.getPostId()).setResumeId(resumeId).setSourceRemark(reqVO.getSourceRemark())
                .setStatus(HrmRecruitApplicationStatusEnum.SUBMITTED.getStatus())
                .setStatusUpdateTime(LocalDateTime.now()).setVersion(0);
        application.setTenantId(targetTenantId);
        applicationMapper.insert(application);
        candidateService.markRecruitCandidateSubmitted(candidate.getId(), reqVO.getPostId());
        chatService.markApplicationSubmitted(applicantUserId, reqVO.getPostId(), candidate.getId(), application.getId());
        writeLog(application, null, application.getStatus(), "PORTAL_CREATE", null);
        return application.getId();
    }

    @Override
    public PageResult<HrmRecruitApplicationDO> getPortalApplicationPage(Long applicantUserId,
                                                                          HrmPortalRecruitApplicationPageReqVO reqVO) {
        offerService.expireOverdueOffers(applicantUserId);
        HrmRecruitApplicationPageReqVO pageReq = new HrmRecruitApplicationPageReqVO();
        pageReq.setPageNo(reqVO.getPageNo());
        pageReq.setPageSize(reqVO.getPageSize());
        pageReq.setStatus(reqVO.getStatus());
        return TenantUtils.executeIgnore(() -> applicationMapper.selectPageByApplicantUserId(applicantUserId, pageReq));
    }

    @Override
    public HrmRecruitApplicationDO getPortalApplication(Long applicantUserId, Long id) {
        offerService.expireOverdueOffers(applicantUserId);
        HrmRecruitApplicationDO application = TenantUtils.executeIgnore(
                () -> applicationMapper.selectByIdAndApplicantUserId(id, applicantUserId));
        if (application == null) throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        return application;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawPortalApplication(Long applicantUserId, Long id, String reason) {
        HrmRecruitApplicationDO application = getPortalApplication(applicantUserId, id);
        Long targetTenantId = application.getTenantId();
        if (targetTenantId == null) {
            throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        }
        TenantUtils.execute(targetTenantId,
                () -> withdraw(new HrmRecruitApplicationStatusReqVO().setId(application.getId()).setReason(reason)));
    }

    private void transition(HrmRecruitApplicationStatusReqVO reqVO, HrmRecruitApplicationStatusEnum target,
                            String action, boolean reasonRequired) {
        HrmRecruitApplicationDO application = applicationMapper.selectByIdForUpdate(reqVO.getId());
        if (application == null) {
            throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        }
        if (reasonRequired && StrUtil.isBlank(reqVO.getReason())) {
            throw exception(RECRUIT_APPLICATION_REASON_REQUIRED);
        }
        HrmRecruitApplicationStatusEnum current = HrmRecruitApplicationStatusEnum
                .valueOfStatus(application.getStatus());
        if (!isAllowed(current, target)) {
            throw exception(RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID, target.getName());
        }
        if (target == HrmRecruitApplicationStatusEnum.ELIMINATED
                && (current == HrmRecruitApplicationStatusEnum.INTERVIEW_SCHEDULED
                || current == HrmRecruitApplicationStatusEnum.INTERVIEWING)) {
            interviewService.finishCurrentRecruitInterviewForElimination(
                    application.getId(), application.getCandidateId(), reqVO.getReason());
        }
        HrmRecruitApplicationDO update = new HrmRecruitApplicationDO().setId(application.getId())
                .setStatus(target.getStatus()).setStatusUpdateTime(LocalDateTime.now())
                .setEliminateReason(isRejectionStatus(target) ? reqVO.getReason() : null)
                .setWithdrawReason(target == HrmRecruitApplicationStatusEnum.WITHDRAWN ? reqVO.getReason() : null)
                .setVersion(application.getVersion() == null ? 1 : application.getVersion() + 1);
        if (applicationMapper.updateStatusWithVersion(application.getId(), application.getVersion(), update) == 0) {
            throw exception(RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID, target.getName());
        }
        writeLog(application, application.getStatus(), target.getStatus(), action, reqVO.getReason());
        candidateService.syncRecruitCandidateProjection(application.getCandidateId());
    }

    private boolean isRejectionStatus(HrmRecruitApplicationStatusEnum status) {
        return status == HrmRecruitApplicationStatusEnum.RESUME_REJECTED
                || status == HrmRecruitApplicationStatusEnum.INTERVIEW_REJECTED
                || status == HrmRecruitApplicationStatusEnum.OFFER_REJECTED
                || status == HrmRecruitApplicationStatusEnum.OFFER_WITHDRAWN
                || status == HrmRecruitApplicationStatusEnum.OFFER_EXPIRED
                || status == HrmRecruitApplicationStatusEnum.OFFER_CANDIDATE_REJECTED
                || status == HrmRecruitApplicationStatusEnum.ELIMINATED;
    }

    private boolean isAllowed(HrmRecruitApplicationStatusEnum current, HrmRecruitApplicationStatusEnum target) {
        if (current == null || current.isTerminal()) {
            return false;
        }
        if (target == HrmRecruitApplicationStatusEnum.ELIMINATED) {
            return current != HrmRecruitApplicationStatusEnum.OFFER_SENT
                    && current != HrmRecruitApplicationStatusEnum.PENDING_ONBOARD;
        }
        if (current == HrmRecruitApplicationStatusEnum.SUBMITTED) {
            return target == HrmRecruitApplicationStatusEnum.SCREENING
                    || target == HrmRecruitApplicationStatusEnum.RESUME_REJECTED
                    || target == HrmRecruitApplicationStatusEnum.ELIMINATED
                    || target == HrmRecruitApplicationStatusEnum.WITHDRAWN;
        }
        if (current == HrmRecruitApplicationStatusEnum.SCREENING) {
            return target == HrmRecruitApplicationStatusEnum.SCREENING_PASSED
                    || target == HrmRecruitApplicationStatusEnum.RESUME_REJECTED
                    || target == HrmRecruitApplicationStatusEnum.ELIMINATED
                    || target == HrmRecruitApplicationStatusEnum.WITHDRAWN;
        }
        if (current == HrmRecruitApplicationStatusEnum.SCREENING_PASSED) {
            return target == HrmRecruitApplicationStatusEnum.PENDING_INTERVIEW
                    || target == HrmRecruitApplicationStatusEnum.INTERVIEW_SCHEDULED
                    || target == HrmRecruitApplicationStatusEnum.PASSED;
        }
        if (current == HrmRecruitApplicationStatusEnum.NEXT_INTERVIEW) {
            return target == HrmRecruitApplicationStatusEnum.PENDING_INTERVIEW;
        }
        if (current == HrmRecruitApplicationStatusEnum.PENDING_INTERVIEW) {
            return target == HrmRecruitApplicationStatusEnum.INTERVIEW_SCHEDULED
                    || target == HrmRecruitApplicationStatusEnum.PASSED;
        }
        if (current == HrmRecruitApplicationStatusEnum.INTERVIEW_CANCELED
                || current == HrmRecruitApplicationStatusEnum.INTERVIEW_ABSENT) {
            return target == HrmRecruitApplicationStatusEnum.PENDING_INTERVIEW
                    || target == HrmRecruitApplicationStatusEnum.INTERVIEW_SCHEDULED;
        }
        if (current == HrmRecruitApplicationStatusEnum.INTERVIEW_SCHEDULED) {
            return target == HrmRecruitApplicationStatusEnum.INTERVIEWING
                    || target == HrmRecruitApplicationStatusEnum.INTERVIEW_CANCELED
                    || target == HrmRecruitApplicationStatusEnum.INTERVIEW_ABSENT;
        }
        if (current == HrmRecruitApplicationStatusEnum.INTERVIEWING) {
            return target == HrmRecruitApplicationStatusEnum.INTERVIEW_EVALUATING
                    || target == HrmRecruitApplicationStatusEnum.INTERVIEW_CANCELED
                    || target == HrmRecruitApplicationStatusEnum.INTERVIEW_ABSENT
                    || target == HrmRecruitApplicationStatusEnum.INTERVIEW_REJECTED
                    || target == HrmRecruitApplicationStatusEnum.ELIMINATED
                    || target == HrmRecruitApplicationStatusEnum.WITHDRAWN;
        }
        if (current == HrmRecruitApplicationStatusEnum.INTERVIEW_EVALUATING) {
            return target == HrmRecruitApplicationStatusEnum.NEXT_INTERVIEW
                    || target == HrmRecruitApplicationStatusEnum.FINAL_INTERVIEW_PASSED
                    || target == HrmRecruitApplicationStatusEnum.INTERVIEW_REJECTED;
        }
        if (current == HrmRecruitApplicationStatusEnum.FINAL_INTERVIEW_PASSED) {
            return target == HrmRecruitApplicationStatusEnum.PENDING_OFFER;
        }
        if (current == HrmRecruitApplicationStatusEnum.PASSED) {
            return target == HrmRecruitApplicationStatusEnum.INTERVIEW_SCHEDULED
                    || target == HrmRecruitApplicationStatusEnum.OFFER_APPROVING
                    || target == HrmRecruitApplicationStatusEnum.OFFER_SENT
                    || target == HrmRecruitApplicationStatusEnum.PENDING_OFFER;
        }
        if (current == HrmRecruitApplicationStatusEnum.PENDING_OFFER) {
            return target == HrmRecruitApplicationStatusEnum.OFFER_APPROVING
                    || target == HrmRecruitApplicationStatusEnum.OFFER_SENT
                    || target == HrmRecruitApplicationStatusEnum.OFFER_WITHDRAWN
                    || target == HrmRecruitApplicationStatusEnum.PENDING_OFFER;
        }
        if (current == HrmRecruitApplicationStatusEnum.OFFER_APPROVING) {
            return target == HrmRecruitApplicationStatusEnum.OFFER_SENT
                    || target == HrmRecruitApplicationStatusEnum.OFFER_REJECTED
                    || target == HrmRecruitApplicationStatusEnum.OFFER_WITHDRAWN
                    || target == HrmRecruitApplicationStatusEnum.PENDING_OFFER;
        }
        if (current == HrmRecruitApplicationStatusEnum.OFFER_SENT) {
            return target == HrmRecruitApplicationStatusEnum.PENDING_ONBOARD
                    || target == HrmRecruitApplicationStatusEnum.OFFER_REJECTED
                    || target == HrmRecruitApplicationStatusEnum.OFFER_WITHDRAWN
                    || target == HrmRecruitApplicationStatusEnum.OFFER_EXPIRED
                    || target == HrmRecruitApplicationStatusEnum.OFFER_CANDIDATE_REJECTED;
        }
        return (current == HrmRecruitApplicationStatusEnum.INTERVIEWING
                && target == HrmRecruitApplicationStatusEnum.PASSED)
                || (current == HrmRecruitApplicationStatusEnum.PASSED
                && target == HrmRecruitApplicationStatusEnum.INTERVIEWING);
    }

    private void writeLog(HrmRecruitApplicationDO application, String fromStatus, String toStatus,
                          String action, String reason) {
        HrmRecruitApplicationLogDO log = new HrmRecruitApplicationLogDO().setApplicationId(application.getId())
                .setFromStatus(fromStatus).setToStatus(toStatus).setAction(action).setReason(reason);
        log.setTenantId(application.getTenantId() != null
                ? application.getTenantId() : TenantContextHolder.getTenantId());
        applicationLogMapper.insert(log);
    }
}
