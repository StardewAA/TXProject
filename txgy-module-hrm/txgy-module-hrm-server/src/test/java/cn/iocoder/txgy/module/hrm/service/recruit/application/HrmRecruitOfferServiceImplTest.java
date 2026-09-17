package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitOfferMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitOfferApprovalStatusEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_OFFER_DATA_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_OFFER_POST_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(HrmRecruitOfferServiceImpl.class)
public class HrmRecruitOfferServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmRecruitOfferServiceImpl offerService;
    @Resource
    private HrmRecruitOfferMapper offerMapper;

    @MockitoBean
    private HrmRecruitApplicationService applicationService;
    @MockitoBean
    private HrmEmployeeService employeeService;
    @MockitoBean
    private HrmRecruitCandidateService candidateService;
    @MockitoBean
    private HrmRecruitPostService postService;

    @Test
    public void testCreate_rejectsPostMismatch() {
        HrmRecruitApplicationDO application = application(HrmRecruitApplicationStatusEnum.PASSED.getStatus());
        when(applicationService.getApplication(application.getId())).thenReturn(application);

        HrmRecruitOfferSaveReqVO request = validRequest(application).setPostId(randomLongId());

        assertServiceException(() -> offerService.create(request), RECRUIT_OFFER_POST_MISMATCH);
    }

    @Test
    public void testCreate_rejectsInvalidBusinessData() {
        HrmRecruitApplicationDO application = application(HrmRecruitApplicationStatusEnum.PASSED.getStatus());
        when(applicationService.getApplication(application.getId())).thenReturn(application);

        HrmRecruitOfferSaveReqVO request = validRequest(application).setSalaryAmount(BigDecimal.ZERO);

        assertServiceException(() -> offerService.create(request), RECRUIT_OFFER_DATA_INVALID);
    }

    @Test
    public void testOfferStateMachine_createSubmitApproveSend() {
        HrmRecruitApplicationDO application = application(HrmRecruitApplicationStatusEnum.PASSED.getStatus());
        when(applicationService.getApplication(application.getId())).thenReturn(application);

        Long offerId = offerService.create(validRequest(application));
        offerService.submit(offerId);
        assertEquals(HrmRecruitOfferApprovalStatusEnum.APPROVING.getStatus(), offerMapper.selectById(offerId).getApprovalStatus());

        offerService.approvalCallback(offerId, "process-1", 1, true, null);
        assertEquals(HrmRecruitOfferApprovalStatusEnum.APPROVED.getStatus(), offerMapper.selectById(offerId).getApprovalStatus());

        offerService.send(offerId);
        assertEquals(HrmRecruitOfferApprovalStatusEnum.SENT.getStatus(), offerMapper.selectById(offerId).getApprovalStatus());
        verify(applicationService).markPendingOffer(application.getId());
        verify(applicationService).markOfferApproving(application.getId());
        verify(applicationService).markOfferSent(application.getId());
    }

    @Test
    public void testApprovalReject_recordsReasonAndStatus() {
        HrmRecruitOfferDO offer = offer(HrmRecruitOfferApprovalStatusEnum.APPROVING.getStatus());
        offer.setApprovalVersion(2);
        offerMapper.insert(offer);

        offerService.approvalCallback(offer.getId(), "process-2", 2, false, "预算未通过");

        HrmRecruitOfferDO updated = offerMapper.selectById(offer.getId());
        assertEquals(HrmRecruitOfferApprovalStatusEnum.REJECTED.getStatus(), updated.getApprovalStatus());
        assertEquals("预算未通过", updated.getRemark());
        verify(applicationService).markOfferRejected(offer.getApplicationId(), "预算未通过");
    }

    @Test
    public void testWithdraw_sentOffer_recordsWithdrawnStatus() {
        HrmRecruitOfferDO offer = offer(HrmRecruitOfferApprovalStatusEnum.SENT.getStatus());
        offerMapper.insert(offer);

        offerService.withdraw(offer.getId(), "岗位取消招聘");

        assertEquals(HrmRecruitOfferApprovalStatusEnum.WITHDRAWN.getStatus(), offerMapper.selectById(offer.getId()).getApprovalStatus());
        verify(applicationService).markOfferWithdrawn(offer.getApplicationId(), "岗位取消招聘");
    }

    @Test
    public void testWithdraw_draftOffer_recordsWithdrawnStatus() {
        HrmRecruitOfferDO offer = offer(HrmRecruitOfferApprovalStatusEnum.DRAFT.getStatus());
        offerMapper.insert(offer);

        offerService.withdraw(offer.getId(), "撤回草稿");

        assertEquals(HrmRecruitOfferApprovalStatusEnum.WITHDRAWN.getStatus(), offerMapper.selectById(offer.getId()).getApprovalStatus());
        verify(applicationService).markOfferWithdrawn(offer.getApplicationId(), "撤回草稿");
    }

    @Test
    public void testExpire_recordsExpiredStatus() {
        HrmRecruitOfferDO offer = offer(HrmRecruitOfferApprovalStatusEnum.SENT.getStatus());
        offerMapper.insert(offer);

        offerService.expire(offer.getId(), "超过有效期");

        assertEquals(HrmRecruitOfferApprovalStatusEnum.EXPIRED.getStatus(), offerMapper.selectById(offer.getId()).getApprovalStatus());
        verify(applicationService).markOfferExpired(offer.getApplicationId(), "超过有效期");
    }

    @Test
    public void testCandidateReject_recordsCandidateRejectedStatus() {
        Long userId = randomLongId();
        HrmRecruitApplicationDO application = application(HrmRecruitApplicationStatusEnum.OFFER_SENT.getStatus());
        application.setApplicantUserId(userId);
        HrmRecruitOfferDO offer = offer(HrmRecruitOfferApprovalStatusEnum.SENT.getStatus())
                .setApplicationId(application.getId()).setValidUntil(LocalDate.now().plusDays(2));
        offerMapper.insert(offer);
        when(applicationService.getApplication(application.getId())).thenReturn(application);

        offerService.rejectByCandidate(offer.getId(), userId, "薪资不符合预期");

        assertEquals(HrmRecruitOfferApprovalStatusEnum.CANDIDATE_REJECTED.getStatus(),
                offerMapper.selectById(offer.getId()).getApprovalStatus());
        verify(applicationService).markOfferCandidateRejected(application.getId(), "薪资不符合预期");
    }

    private HrmRecruitApplicationDO application(String status) {
        HrmRecruitApplicationDO application = new HrmRecruitApplicationDO().setId(randomLongId())
                .setCandidateId(randomLongId()).setPostId(randomLongId()).setStatus(status).setVersion(0);
        application.setTenantId(1L);
        return application;
    }

    private HrmRecruitOfferSaveReqVO validRequest(HrmRecruitApplicationDO application) {
        return new HrmRecruitOfferSaveReqVO().setApplicationId(application.getId())
                .setPostId(application.getPostId()).setSalaryAmount(new BigDecimal("12000.00"))
                .setSalaryUnit(1).setPlannedOnboardDate(LocalDate.now().plusDays(7))
                .setValidUntil(LocalDate.now().plusDays(14)).setRemark("Offer测试");
    }

    private HrmRecruitOfferDO offer(String status) {
        return new HrmRecruitOfferDO().setApplicationId(randomLongId()).setOfferNo("OFF-TEST-" + randomLongId())
                .setApprovalStatus(status).setSalaryAmount(new BigDecimal("12000.00"))
                .setSalaryUnit(1).setPostId(randomLongId()).setValidUntil(LocalDate.now().plusDays(2))
                .setApprovalVersion(1);
    }
}
