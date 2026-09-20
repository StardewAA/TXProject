package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationStatusReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitOfferMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitOfferApprovalStatusEnum;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeVersionService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitInterviewService;
import cn.iocoder.txgy.module.hrm.service.recruit.chat.HrmRecruitChatService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Import({HrmRecruitApplicationServiceImpl.class, HrmRecruitOfferServiceImpl.class})
class HrmRecruitOfferFlowAcceptanceTest extends BaseDbUnitTest {

    @Resource
    private HrmRecruitApplicationServiceImpl applicationService;
    @Resource
    private HrmRecruitOfferServiceImpl offerService;
    @Resource
    private HrmRecruitApplicationMapper applicationMapper;
    @Resource
    private HrmRecruitOfferMapper offerMapper;

    @MockitoBean
    private HrmRecruitCandidateService candidateService;
    @MockitoBean
    private HrmRecruitPostService postService;
    @MockitoBean
    private HrmRecruitResumeService resumeService;
    @MockitoBean
    private HrmPersonalResumeService personalResumeService;
    @MockitoBean
    private HrmPersonalResumeVersionService personalResumeVersionService;
    @MockitoBean
    private HrmRecruitChatService chatService;
    @MockitoBean
    private HrmRecruitInterviewService interviewService;
    @MockitoBean
    private HrmEmployeeService employeeService;

    @Test
    void fullFlowAcceptShouldReachPendingOnboard() {
        Long applicantUserId = randomLongId();
        Long applicationId = createApplication(applicantUserId);
        advanceApplicationToFinalInterviewPassed(applicationId);

        Long offerId = createAndSendOffer(applicationId);
        offerService.acceptByCandidate(offerId, applicantUserId);

        assertEquals(HrmRecruitOfferApprovalStatusEnum.CONFIRMED.getStatus(),
                offerMapper.selectById(offerId).getApprovalStatus());
        assertEquals(HrmRecruitApplicationStatusEnum.PENDING_ONBOARD.getStatus(),
                applicationMapper.selectById(applicationId).getStatus());
    }

    @Test
    void fullFlowRejectShouldReachCandidateRejected() {
        Long applicantUserId = randomLongId();
        Long applicationId = createApplication(applicantUserId);
        advanceApplicationToFinalInterviewPassed(applicationId);

        Long offerId = createAndSendOffer(applicationId);
        offerService.rejectByCandidate(offerId, applicantUserId, "薪资不符合预期");

        assertEquals(HrmRecruitOfferApprovalStatusEnum.CANDIDATE_REJECTED.getStatus(),
                offerMapper.selectById(offerId).getApprovalStatus());
        assertEquals(HrmRecruitApplicationStatusEnum.OFFER_CANDIDATE_REJECTED.getStatus(),
                applicationMapper.selectById(applicationId).getStatus());
    }

    private Long createApplication(Long applicantUserId) {
        Long candidateId = randomLongId();
        Long postId = randomLongId();
        Long applicationId = applicationService.createApplication(new HrmRecruitApplicationCreateReqVO()
                .setCandidateId(candidateId).setPostId(postId).setResumeId(randomLongId()));
        HrmRecruitApplicationDO update = new HrmRecruitApplicationDO().setId(applicationId);
        update.setApplicantUserId(applicantUserId);
        update.setTenantId(1L);
        applicationMapper.updateById(update);
        assertNotNull(applicationMapper.selectById(applicationId));
        return applicationId;
    }

    private void advanceApplicationToFinalInterviewPassed(Long applicationId) {
        HrmRecruitApplicationStatusReqVO request = new HrmRecruitApplicationStatusReqVO().setId(applicationId);
        applicationService.startScreening(request);
        applicationService.passScreening(request);
        applicationService.enterInterview(request);
        applicationService.markInterviewScheduled(applicationId);
        applicationService.markInterviewStarted(applicationId);
        applicationService.markInterviewEvaluating(applicationId);
        applicationService.markFinalInterviewPassed(applicationId);
        assertEquals(HrmRecruitApplicationStatusEnum.FINAL_INTERVIEW_PASSED.getStatus(),
                applicationMapper.selectById(applicationId).getStatus());
    }

    private Long createAndSendOffer(Long applicationId) {
        HrmRecruitApplicationDO application = applicationMapper.selectById(applicationId);
        HrmRecruitOfferSaveReqVO request = new HrmRecruitOfferSaveReqVO()
                .setApplicationId(applicationId).setPostId(application.getPostId())
                .setSalaryAmount(new BigDecimal("12000.00")).setSalaryUnit(1)
                .setPlannedOnboardDate(LocalDate.now().plusDays(7))
                .setValidUntil(LocalDate.now().plusDays(14));
        Long offerId = offerService.create(request);
        offerService.submit(offerId);
        HrmRecruitOfferDO approving = offerMapper.selectById(offerId);
        offerService.approvalCallback(offerId, "acceptance-process-" + offerId,
                approving.getApprovalVersion(), true, null);
        offerService.send(offerId);
        assertEquals(HrmRecruitApplicationStatusEnum.OFFER_SENT.getStatus(),
                applicationMapper.selectById(applicationId).getStatus());
        return offerId;
    }
}
