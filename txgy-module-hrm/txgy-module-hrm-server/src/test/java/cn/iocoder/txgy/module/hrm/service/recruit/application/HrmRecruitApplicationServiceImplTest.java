package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplyReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationLogDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationLogMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
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
import static org.mockito.Mockito.when;

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

    @Test
    public void testCreatePortalApplication_routesToPostTenant() {
        Long applicantUserId = randomLongId();
        HrmRecruitPostDO post = randomPojo(HrmRecruitPostDO.class, item -> {
            item.setTenantId(202L);
            item.setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        });
        HrmPortalRecruitApplyReqVO reqVO = randomPojo(HrmPortalRecruitApplyReqVO.class, item -> item
                .setPostId(post.getId()).setResumeFileName("resume.pdf").setResumeFileUrl("resume.pdf"));
        HrmRecruitCandidateDO candidate = randomPojo(HrmRecruitCandidateDO.class, item -> item
                .setId(randomLongId()).setMobile(reqVO.getMobile()));

        when(postService.getPortalRecruitPostPublished(eq(post.getId()))).thenReturn(post);
        when(postService.validateRecruitPostPublished(eq(post.getId()))).thenReturn(post);
        when(candidateService.getRecruitCandidateByMobile(eq(reqVO.getMobile()))).thenReturn(candidate);
        when(resumeService.createRecruitResume(any())).thenReturn(randomLongId());

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
}
