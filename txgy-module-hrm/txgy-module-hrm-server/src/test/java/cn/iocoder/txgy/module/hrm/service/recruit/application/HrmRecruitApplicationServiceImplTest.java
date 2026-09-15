package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplyReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationCreateReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationLogDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationLogMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.chat.HrmRecruitChatService;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitOfferService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @MockitoBean
    private HrmRecruitCandidateService candidateService;
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
}
