package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplyReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationStatusReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationLogDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationLogMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.candidate.HrmRecruitCandidateMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.candidate.HrmRecruitCandidateMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitInterviewService;
import cn.iocoder.txgy.module.hrm.service.recruit.chat.HrmRecruitChatService;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitOfferService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_APPLICATION_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_APPLY_NOT_QUALIFIED;

@Import(HrmRecruitApplicationServiceImpl.class)
public class HrmRecruitApplicationServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmRecruitApplicationServiceImpl applicationService;
    @Resource
    private HrmRecruitApplicationMapper applicationMapper;
    @Resource
    private HrmRecruitApplicationLogMapper applicationLogMapper;
    @Resource
    private HrmRecruitCandidateMapper candidateMapper;

    @MockitoBean
    private HrmRecruitCandidateService candidateService;
    @MockitoBean
    private HrmRecruitInterviewService interviewService;
    @MockitoBean
    private HrmRecruitPostService postService;
    @MockitoBean
    private HrmRecruitResumeService resumeService;
    @MockitoBean
    private HrmPersonalResumeService personalResumeService;
    @MockitoBean
    private HrmRecruitChatService chatService;
    @MockitoBean
    private HrmRecruitOfferService offerService;

    @Test
    public void testCreateApplicationFromRecruitChat_linksPortalApplicantAndIsIdempotent() {
        Long applicantUserId = randomLongId();
        Long candidateId = randomLongId();
        Long postId = randomLongId();
        Long firstResumeId = randomLongId();
        Long secondResumeId = randomLongId();
        when(postService.validateRecruitPostExists(postId)).thenReturn(new HrmRecruitPostDO().setId(postId));

        Long firstApplicationId = applicationService.createApplicationFromRecruitChat(
                applicantUserId, candidateId, postId, firstResumeId);
        Long secondApplicationId = applicationService.createApplicationFromRecruitChat(
                applicantUserId, candidateId, postId, secondResumeId);

        assertEquals(firstApplicationId, secondApplicationId);
        HrmRecruitApplicationDO application = applicationMapper.selectById(firstApplicationId);
        assertEquals(applicantUserId, application.getApplicantUserId());
        assertEquals(candidateId, application.getCandidateId());
        assertEquals(postId, application.getPostId());
        assertEquals(firstResumeId, application.getResumeId());
        assertEquals(HrmRecruitApplicationStatusEnum.SUBMITTED.getStatus(), application.getStatus());
        assertEquals(1L, applicationLogMapper.selectCount(new LambdaQueryWrapper<HrmRecruitApplicationLogDO>()
                .eq(HrmRecruitApplicationLogDO::getApplicationId, firstApplicationId)));
    }

    @Test
    public void testCreatePortalApplication_routesToPostTenant() {
        Long applicantUserId = randomLongId();
        HrmRecruitPostDO post = randomPojo(HrmRecruitPostDO.class, item -> {
            item.setTenantId(202L);
            item.setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        });
        HrmPortalRecruitApplyReqVO reqVO = randomPojo(HrmPortalRecruitApplyReqVO.class, item -> item
                .setPostId(post.getId()).setResumeId(null).setResumeFileName("resume.pdf").setResumeFileUrl("resume.pdf"));
        HrmRecruitCandidateDO candidate = randomPojo(HrmRecruitCandidateDO.class, item -> item
                .setId(randomLongId()).setMobile(reqVO.getMobile()));

        when(postService.getPortalRecruitPostPublished(eq(post.getId()))).thenReturn(post);
        when(postService.validateRecruitPostPublished(eq(post.getId()))).thenReturn(post);
        when(candidateService.getRecruitCandidateByMobile(eq(reqVO.getMobile()))).thenReturn(candidate);
        when(resumeService.createRecruitResume(any())).thenReturn(randomLongId());
        when(chatService.hasApplyQualification(eq(applicantUserId), eq(post.getId()))).thenReturn(true);

        TenantContextHolder.setTenantId(999L);
        Long applicationId;
        try {
            applicationId = applicationService.createPortalApplication(applicantUserId, reqVO);
            assertEquals(999L, TenantContextHolder.getTenantId());
        } finally {
            TenantContextHolder.clear();
        }

        HrmRecruitApplicationDO application = TenantUtils.executeIgnore(
                () -> applicationMapper.selectById(applicationId));
        assertNotNull(application);
        assertEquals(202L, application.getTenantId());
        assertEquals(applicantUserId, application.getApplicantUserId());
        assertEquals(post.getId(), application.getPostId());
        assertEquals(HrmRecruitApplicationStatusEnum.SUBMITTED.getStatus(), application.getStatus());
        assertEquals(candidate.getId(), application.getCandidateId());
        assertNotNull(TenantUtils.executeIgnore(() -> applicationLogMapper.selectOne(
                new LambdaQueryWrapper<HrmRecruitApplicationLogDO>()
                        .eq(HrmRecruitApplicationLogDO::getApplicationId, applicationId))));
        verify(postService).getPortalRecruitPostPublished(post.getId());
        verify(postService).validateRecruitPostPublished(post.getId());
        verify(chatService).markApplicationSubmitted(
                applicantUserId, post.getId(), candidate.getId(), applicationId);
    }

    @Test
    public void testCreatePortalApplication_withoutQualification_rejected() {
        Long applicantUserId = randomLongId();
        HrmRecruitPostDO post = randomPojo(HrmRecruitPostDO.class, item -> {
            item.setTenantId(203L);
            item.setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        });
        HrmPortalRecruitApplyReqVO reqVO = randomPojo(HrmPortalRecruitApplyReqVO.class, item -> item
                .setPostId(post.getId()).setResumeId(null).setResumeFileName("resume.pdf").setResumeFileUrl("resume.pdf"));
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);
        when(postService.validateRecruitPostPublished(post.getId())).thenReturn(post);
        when(chatService.hasApplyQualification(applicantUserId, post.getId())).thenReturn(false);

        assertServiceException(() -> applicationService.createPortalApplication(applicantUserId, reqVO),
                RECRUIT_CHAT_APPLY_NOT_QUALIFIED);
        verify(chatService).hasApplyQualification(applicantUserId, post.getId());
    }

    @Test
    public void testCreatePortalApplication_duplicate_rejected() {
        Long applicantUserId = randomLongId();
        HrmRecruitPostDO post = randomPojo(HrmRecruitPostDO.class, item -> {
            item.setTenantId(204L);
            item.setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        });
        HrmPortalRecruitApplyReqVO reqVO = randomPojo(HrmPortalRecruitApplyReqVO.class, item -> item
                .setPostId(post.getId()).setResumeId(null).setResumeFileName("resume.pdf").setResumeFileUrl("resume.pdf"));
        HrmRecruitCandidateDO candidate = randomPojo(HrmRecruitCandidateDO.class, item -> item
                .setId(randomLongId()).setMobile(reqVO.getMobile()));
        HrmRecruitApplicationDO processing = randomPojo(HrmRecruitApplicationDO.class, item -> item
                .setApplicantUserId(applicantUserId).setCandidateId(candidate.getId()).setPostId(post.getId())
                .setStatus(HrmRecruitApplicationStatusEnum.SUBMITTED.getStatus()).setTenantId(post.getTenantId()));
        TenantUtils.execute(post.getTenantId(), () -> applicationMapper.insert(processing));
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);
        when(postService.validateRecruitPostPublished(post.getId())).thenReturn(post);
        when(chatService.hasApplyQualification(applicantUserId, post.getId())).thenReturn(true);
        when(candidateService.getRecruitCandidateByMobile(reqVO.getMobile())).thenReturn(candidate);

        assertServiceException(() -> applicationService.createPortalApplication(applicantUserId, reqVO),
                RECRUIT_APPLICATION_DUPLICATE);
    }

    @Test
    public void testCreateApplication_allowsSameCandidateAcrossPosts() {
        Long candidateId = randomLongId();
        Long firstPostId = randomLongId();
        Long secondPostId = randomLongId();
        when(postService.validateRecruitPostPublished(any())).thenReturn(randomPojo(HrmRecruitPostDO.class));

        Long firstApplicationId = applicationService.createApplication(new HrmRecruitApplicationCreateReqVO()
                .setCandidateId(candidateId).setPostId(firstPostId).setResumeId(randomLongId()));
        Long secondApplicationId = applicationService.createApplication(new HrmRecruitApplicationCreateReqVO()
                .setCandidateId(candidateId).setPostId(secondPostId).setResumeId(randomLongId()));

        assertNotNull(firstApplicationId);
        assertNotNull(secondApplicationId);
        assertEquals(2L, applicationMapper.selectCount(new LambdaQueryWrapper<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getCandidateId, candidateId)));
    }

    @Test
    public void testCreatePortalApplication_concurrentSamePostCreatesOnlyOneApplication() throws Exception {
        Long applicantUserId = randomLongId();
        HrmRecruitPostDO post = randomPojo(HrmRecruitPostDO.class, item -> {
            item.setTenantId(205L);
            item.setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        });
        HrmRecruitCandidateDO candidate = randomPojo(HrmRecruitCandidateDO.class, item -> {
            item.setId(randomLongId()).setName("并发候选人").setMobile("13900009999")
                    .setSex(1).setEducation(5).setPostId(post.getId());
            item.setTenantId(post.getTenantId());
        });
        TenantUtils.execute(post.getTenantId(), () -> candidateMapper.insert(candidate));
        HrmPortalRecruitApplyReqVO reqVO = randomPojo(HrmPortalRecruitApplyReqVO.class, item -> item
                .setPostId(post.getId()).setMobile(candidate.getMobile()).setResumeId(null)
                .setResumeFileName("resume.pdf").setResumeFileUrl("resume.pdf"));

        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);
        when(postService.validateRecruitPostPublished(post.getId())).thenReturn(post);
        when(candidateService.getRecruitCandidateByMobile(candidate.getMobile())).thenReturn(candidate);
        when(chatService.hasApplyQualification(applicantUserId, post.getId())).thenReturn(true);
        when(resumeService.createRecruitResume(any())).thenReturn(randomLongId());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Long> first = executor.submit(() -> {
            start.await();
            return applicationService.createPortalApplication(applicantUserId, reqVO);
        });
        Future<Long> second = executor.submit(() -> {
            start.await();
            return applicationService.createPortalApplication(applicantUserId, reqVO);
        });
        start.countDown();

        int successCount = 0;
        int failureCount = 0;
        for (Future<Long> future : List.of(first, second)) {
            try {
                assertNotNull(future.get());
                successCount++;
            } catch (ExecutionException exception) {
                assertTrue(exception.getCause() instanceof ServiceException);
                assertEquals(RECRUIT_APPLICATION_DUPLICATE.getCode(),
                        ((ServiceException) exception.getCause()).getCode());
                failureCount++;
            }
        }
        executor.shutdownNow();

        assertEquals(1, successCount);
        assertEquals(1, failureCount);
        assertEquals(1L, applicationMapper.selectCount(new LambdaQueryWrapper<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getApplicantUserId, applicantUserId)
                .eq(HrmRecruitApplicationDO::getPostId, post.getId())));
    }

    @Test
    public void testCreateApplication_adminProxyExemptFromQualificationAndAudited() {
        Long candidateId = randomLongId();
        Long postId = randomLongId();
        HrmRecruitApplicationCreateReqVO reqVO = randomPojo(HrmRecruitApplicationCreateReqVO.class, item -> item
                .setCandidateId(candidateId).setPostId(postId));

        Long applicationId = applicationService.createApplication(reqVO);

        HrmRecruitApplicationDO application = applicationMapper.selectById(applicationId);
        assertNotNull(application);
        assertEquals(candidateId, application.getCandidateId());
        assertEquals(postId, application.getPostId());
        assertEquals(HrmRecruitApplicationStatusEnum.SUBMITTED.getStatus(), application.getStatus());
        assertNotNull(applicationLogMapper.selectOne(new LambdaQueryWrapper<HrmRecruitApplicationLogDO>()
                .eq(HrmRecruitApplicationLogDO::getApplicationId, applicationId)
                .eq(HrmRecruitApplicationLogDO::getAction, "CREATE")));
        verify(candidateService).validateRecruitCandidateExists(candidateId);
        verify(postService).validateRecruitPostPublished(postId);
        verifyNoInteractions(chatService);
    }

    @Test
    public void testSkipInterview_marksApplicationPassedWithoutInterview() {
        HrmRecruitApplicationDO application = randomPojo(HrmRecruitApplicationDO.class, item -> item
                .setStatus(HrmRecruitApplicationStatusEnum.SCREENING_PASSED.getStatus())
                .setVersion(0));
        applicationMapper.insert(application);

        applicationService.skipInterview(application.getId());

        HrmRecruitApplicationDO updated = applicationMapper.selectById(application.getId());
        assertEquals(HrmRecruitApplicationStatusEnum.PASSED.getStatus(), updated.getStatus());
        assertNotNull(applicationLogMapper.selectOne(new LambdaQueryWrapper<HrmRecruitApplicationLogDO>()
                .eq(HrmRecruitApplicationLogDO::getApplicationId, application.getId())
                .eq(HrmRecruitApplicationLogDO::getAction, "INTERVIEW_SKIPPED")));
    }

    @Test
    public void testGetCurrentRecruitApplication_prefersProcessingApplicationAcrossPosts() {
        Long candidateId = randomLongId();
        HrmRecruitApplicationDO closed = randomPojo(HrmRecruitApplicationDO.class, item -> item
                .setCandidateId(candidateId).setPostId(randomLongId())
                .setStatus(HrmRecruitApplicationStatusEnum.ELIMINATED.getStatus())
                .setStatusUpdateTime(java.time.LocalDateTime.of(2026, 9, 16, 10, 0)));
        HrmRecruitApplicationDO processing = randomPojo(HrmRecruitApplicationDO.class, item -> item
                .setCandidateId(candidateId).setPostId(randomLongId())
                .setStatus(HrmRecruitApplicationStatusEnum.INTERVIEWING.getStatus())
                .setStatusUpdateTime(java.time.LocalDateTime.of(2026, 9, 15, 10, 0)));
        applicationMapper.insert(closed);
        applicationMapper.insert(processing);

        HrmRecruitApplicationDO current = applicationService
                .getCurrentRecruitApplicationByCandidateId(candidateId);

        assertEquals(processing.getId(), current.getId());
    }

    @Test
    public void testRejectResume_persistsReasonOnApplication() {
        HrmRecruitApplicationDO application = randomPojo(HrmRecruitApplicationDO.class, item -> item
                .setStatus(HrmRecruitApplicationStatusEnum.SCREENING.getStatus()).setVersion(0));
        applicationMapper.insert(application);

        applicationService.rejectResume(new HrmRecruitApplicationStatusReqVO()
                .setId(application.getId()).setReason("学历不符合"));

        HrmRecruitApplicationDO updated = applicationMapper.selectById(application.getId());
        assertEquals(HrmRecruitApplicationStatusEnum.RESUME_REJECTED.getStatus(), updated.getStatus());
        assertEquals("学历不符合", updated.getEliminateReason());
    }

    @Test
    public void testEliminateApplication_finishesOnlyItsInterview() {
        HrmRecruitApplicationDO application = randomPojo(HrmRecruitApplicationDO.class, item -> item
                .setCandidateId(randomLongId()).setStatus(HrmRecruitApplicationStatusEnum.INTERVIEWING.getStatus())
                .setVersion(0));
        applicationMapper.insert(application);

        applicationService.eliminate(new HrmRecruitApplicationStatusReqVO()
                .setId(application.getId()).setReason("面试不匹配"));

        verify(interviewService).finishCurrentRecruitInterviewForElimination(
                application.getId(), application.getCandidateId(), "面试不匹配");
        HrmRecruitApplicationDO updated = applicationMapper.selectById(application.getId());
        assertEquals(HrmRecruitApplicationStatusEnum.ELIMINATED.getStatus(), updated.getStatus());
        assertEquals("面试不匹配", updated.getEliminateReason());
    }
}
