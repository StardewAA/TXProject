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
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.candidate.HrmRecruitCandidateSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import jakarta.annotation.Resource;
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
    private HrmRecruitCandidateService candidateService;
    @Resource
    private HrmRecruitPostService postService;
    @Resource
    private HrmRecruitResumeService resumeService;
    @Resource
    private HrmPersonalResumeService personalResumeService;

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
        return application.getId();
    }

    @Override
    public PageResult<HrmRecruitApplicationDO> getApplicationPage(HrmRecruitApplicationPageReqVO reqVO) {
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
    @Transactional(rollbackFor = Exception.class)
    public void startScreening(HrmRecruitApplicationStatusReqVO reqVO) {
        transition(reqVO, HrmRecruitApplicationStatusEnum.SCREENING, "START_SCREENING", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enterInterview(HrmRecruitApplicationStatusReqVO reqVO) {
        transition(reqVO, HrmRecruitApplicationStatusEnum.INTERVIEWING, "ENTER_INTERVIEW", false);
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
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.PASSED, "OFFER_REJECTED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPendingOnboard(Long id) {
        transition(new HrmRecruitApplicationStatusReqVO().setId(id),
                HrmRecruitApplicationStatusEnum.PENDING_ONBOARD, "OFFER_CONFIRMED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOnboarded(Long id, Long employeeId) {
        HrmRecruitApplicationDO application = applicationMapper.selectByIdForUpdate(id);
        if (application == null) throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        if (!HrmRecruitApplicationStatusEnum.PENDING_ONBOARD.getStatus().equals(application.getStatus())) {
            throw exception(RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID, HrmRecruitApplicationStatusEnum.ONBOARDED.getName());
        }
        HrmRecruitApplicationDO update = new HrmRecruitApplicationDO().setId(id)
                .setEmployeeId(employeeId).setStatus(HrmRecruitApplicationStatusEnum.ONBOARDED.getStatus())
                .setStatusUpdateTime(LocalDateTime.now())
                .setVersion((application.getVersion() == null ? 0 : application.getVersion()) + 1);
        if (applicationMapper.updateStatusWithVersion(id, application.getVersion(), update) == 0) {
            throw exception(RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID, HrmRecruitApplicationStatusEnum.ONBOARDED.getName());
        }
        writeLog(application, application.getStatus(), HrmRecruitApplicationStatusEnum.ONBOARDED.getStatus(), "ONBOARDED", null);
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
        HrmRecruitCandidateDO candidate = candidateService.getRecruitCandidateByMobile(reqVO.getMobile());
        if (candidate == null) {
            candidate = new HrmRecruitCandidateDO();
            Long candidateId = candidateService.createRecruitCandidate(new HrmRecruitCandidateSaveReqVO()
                    .setName(reqVO.getName()).setMobile(reqVO.getMobile()).setSex(reqVO.getSex())
                    .setAge(reqVO.getAge()).setEmail(reqVO.getEmail()).setPostId(reqVO.getPostId())
                    .setWorkTime(reqVO.getWorkTime()).setEducation(reqVO.getEducation())
                    .setGraduateSchool(reqVO.getGraduateSchool()).setLatestWorkPlace(reqVO.getLatestWorkPlace()));
            candidate = candidateService.validateRecruitCandidateExists(candidateId);
        }
        HrmRecruitApplicationDO processing = applicationMapper.selectProcessingByApplicantAndPost(
                applicantUserId, reqVO.getPostId(), HrmRecruitApplicationStatusEnum.PROCESSING_STATUSES);
        if (processing == null) {
            processing = applicationMapper.selectProcessingByCandidateAndPost(
                    candidate.getId(), reqVO.getPostId(), HrmRecruitApplicationStatusEnum.PROCESSING_STATUSES);
        }
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
        writeLog(application, null, application.getStatus(), "PORTAL_CREATE", null);
        return application.getId();
    }

    @Override
    public PageResult<HrmRecruitApplicationDO> getPortalApplicationPage(Long applicantUserId,
                                                                          HrmPortalRecruitApplicationPageReqVO reqVO) {
        HrmRecruitApplicationPageReqVO pageReq = new HrmRecruitApplicationPageReqVO();
        pageReq.setPageNo(reqVO.getPageNo());
        pageReq.setPageSize(reqVO.getPageSize());
        pageReq.setStatus(reqVO.getStatus());
        return TenantUtils.executeIgnore(() -> applicationMapper.selectPageByApplicantUserId(applicantUserId, pageReq));
    }

    @Override
    public HrmRecruitApplicationDO getPortalApplication(Long applicantUserId, Long id) {
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
        HrmRecruitApplicationDO update = new HrmRecruitApplicationDO().setId(application.getId())
                .setStatus(target.getStatus()).setStatusUpdateTime(LocalDateTime.now())
                .setEliminateReason(target == HrmRecruitApplicationStatusEnum.ELIMINATED ? reqVO.getReason() : null)
                .setWithdrawReason(target == HrmRecruitApplicationStatusEnum.WITHDRAWN ? reqVO.getReason() : null)
                .setVersion(application.getVersion() == null ? 1 : application.getVersion() + 1);
        if (applicationMapper.updateStatusWithVersion(application.getId(), application.getVersion(), update) == 0) {
            throw exception(RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID, target.getName());
        }
        writeLog(application, application.getStatus(), target.getStatus(), action, reqVO.getReason());
    }

    private boolean isAllowed(HrmRecruitApplicationStatusEnum current, HrmRecruitApplicationStatusEnum target) {
        if (current == null || current.isTerminal()) {
            return false;
        }
        return (current == HrmRecruitApplicationStatusEnum.SUBMITTED && target == HrmRecruitApplicationStatusEnum.SCREENING)
                || (current == HrmRecruitApplicationStatusEnum.SUBMITTED && target == HrmRecruitApplicationStatusEnum.ELIMINATED)
                || (current == HrmRecruitApplicationStatusEnum.SUBMITTED && target == HrmRecruitApplicationStatusEnum.WITHDRAWN)
                || (current == HrmRecruitApplicationStatusEnum.SCREENING && target == HrmRecruitApplicationStatusEnum.INTERVIEWING)
                || (current == HrmRecruitApplicationStatusEnum.SCREENING && target == HrmRecruitApplicationStatusEnum.ELIMINATED)
                || (current == HrmRecruitApplicationStatusEnum.SCREENING && target == HrmRecruitApplicationStatusEnum.WITHDRAWN)
                || (current == HrmRecruitApplicationStatusEnum.INTERVIEWING && target == HrmRecruitApplicationStatusEnum.PASSED)
                || (current == HrmRecruitApplicationStatusEnum.INTERVIEWING && target == HrmRecruitApplicationStatusEnum.ELIMINATED)
                || (current == HrmRecruitApplicationStatusEnum.INTERVIEWING && target == HrmRecruitApplicationStatusEnum.WITHDRAWN)
                || (current == HrmRecruitApplicationStatusEnum.PASSED && target == HrmRecruitApplicationStatusEnum.OFFER_APPROVING)
                || (current == HrmRecruitApplicationStatusEnum.OFFER_APPROVING && target == HrmRecruitApplicationStatusEnum.PASSED)
                || (current == HrmRecruitApplicationStatusEnum.OFFER_APPROVING && target == HrmRecruitApplicationStatusEnum.OFFER_SENT)
                || (current == HrmRecruitApplicationStatusEnum.OFFER_SENT && target == HrmRecruitApplicationStatusEnum.PENDING_ONBOARD);
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
