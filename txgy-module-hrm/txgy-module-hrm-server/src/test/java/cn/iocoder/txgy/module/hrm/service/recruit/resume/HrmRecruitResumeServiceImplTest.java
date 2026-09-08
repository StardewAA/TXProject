package cn.iocoder.txgy.module.hrm.service.recruit.resume;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.resume.HrmRecruitResumeMapper;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_RESUME_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@Import(HrmRecruitResumeServiceImpl.class)
public class HrmRecruitResumeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmRecruitResumeServiceImpl resumeService;
    @Resource
    private HrmRecruitResumeMapper resumeMapper;
    @MockitoBean
    private HrmRecruitCandidateService candidateService;
    @MockitoBean
    private FileApi fileApi;

    @Test
    public void testCreateRecruitResume_incrementsVersionAndReplacesCurrent() {
        Long candidateId = randomLongId();
        when(candidateService.validateRecruitCandidateExistsForUpdate(candidateId)).thenReturn(null);
        resumeMapper.insert(new HrmRecruitResumeDO().setCandidateId(candidateId).setFileName("old.pdf")
                .setFileUrl("https://example.com/old.pdf").setVersionNo(1).setStatus(0));
        Long id = resumeService.createRecruitResume(new HrmRecruitResumeSaveReqVO().setCandidateId(candidateId)
                .setFileName("new.pdf").setFileUrl("https://example.com/new.pdf"));
        HrmRecruitResumeDO saved = resumeMapper.selectById(id);
        assertNotNull(saved);
        assertEquals(2, saved.getVersionNo());
        assertEquals(0, saved.getStatus());
        List<HrmRecruitResumeDO> all = resumeMapper.selectListByCandidateId(candidateId);
        assertEquals(1, all.stream().filter(item -> item.getStatus() == 0).count());
    }

    @Test
    public void testGetAuthorizedRecruitResume_notExists() {
        assertServiceException(() -> resumeService.getAuthorizedRecruitResume(randomLongId()),
                RECRUIT_RESUME_NOT_EXISTS);
    }

    @Test
    public void testGetAuthorizedRecruitResume_returnsPresignedUrlWithoutMutatingRecord() {
        Long candidateId = randomLongId();
        HrmRecruitResumeDO resume = new HrmRecruitResumeDO().setCandidateId(candidateId)
                .setFileName("resume.pdf").setFileUrl("https://files.example/resume.pdf")
                .setVersionNo(1).setStatus(0);
        resumeMapper.insert(resume);
        when(candidateService.validateRecruitCandidateExists(candidateId)).thenReturn(null);
        when(fileApi.presignGetUrl(resume.getFileUrl(), 600))
                .thenReturn(CommonResult.success("https://signed.example/resume.pdf?expires=600"));

        HrmRecruitResumeDO authorized = resumeService.getAuthorizedRecruitResume(resume.getId());

        assertEquals("https://signed.example/resume.pdf?expires=600", authorized.getFileUrl());
        assertEquals("https://files.example/resume.pdf", resumeMapper.selectById(resume.getId()).getFileUrl());
    }

    @Test
    public void testGetRecruitResumeList_legacyResumeUrls() {
        Long candidateId = randomLongId();
        HrmRecruitCandidateDO candidate = new HrmRecruitCandidateDO().setId(candidateId)
                .setResumeUrls(List.of("https://files.example/张三%20简历.pdf"));
        when(candidateService.validateRecruitCandidateExists(candidateId)).thenReturn(candidate);

        List<HrmRecruitResumeDO> resumes = resumeService.getRecruitResumeList(candidateId);

        assertEquals(1, resumes.size());
        assertEquals("张三 简历.pdf", resumes.get(0).getFileName());
        assertEquals(0, resumes.get(0).getLegacyIndex());
        assertEquals(1, resumes.get(0).getVersionNo());
        assertNull(resumes.get(0).getId());
    }

    @Test
    public void testGetAuthorizedLegacyRecruitResume_returnsPresignedUrl() {
        Long candidateId = randomLongId();
        String fileUrl = "https://files.example/legacy-resume.pdf";
        HrmRecruitCandidateDO candidate = new HrmRecruitCandidateDO().setId(candidateId)
                .setResumeUrls(List.of(fileUrl));
        candidate.setTenantId(100L);
        when(candidateService.validateRecruitCandidateExists(candidateId)).thenReturn(candidate);
        when(fileApi.presignGetUrl(fileUrl, 600))
                .thenReturn(CommonResult.success("https://signed.example/legacy-resume.pdf?expires=600"));

        HrmRecruitResumeDO authorized = resumeService.getAuthorizedLegacyRecruitResume(candidateId, 0);

        assertEquals("legacy-resume.pdf", authorized.getFileName());
        assertEquals("https://signed.example/legacy-resume.pdf?expires=600", authorized.getFileUrl());
        assertEquals(0, authorized.getLegacyIndex());
    }

    @Test
    public void testGetAuthorizedRecruitResume_rejectsMismatchedCandidateTenant() {
        Long candidateId = randomLongId();
        HrmRecruitResumeDO resume = new HrmRecruitResumeDO().setCandidateId(candidateId)
                .setFileName("resume.pdf").setFileUrl("resume.pdf")
                .setVersionNo(1).setStatus(0);
        resume.setTenantId(100L);
        resumeMapper.insert(resume);
        HrmRecruitCandidateDO otherTenantCandidate = new HrmRecruitCandidateDO().setId(candidateId);
        otherTenantCandidate.setTenantId(200L);
        when(candidateService.validateRecruitCandidateExists(candidateId)).thenReturn(otherTenantCandidate);

        assertServiceException(() -> resumeService.getAuthorizedRecruitResume(resume.getId()),
                RECRUIT_RESUME_NOT_EXISTS);
    }

    @Test
    public void testGetRecruitResumeList_filtersMismatchedTenantRecords() {
        Long candidateId = randomLongId();
        HrmRecruitCandidateDO candidate = new HrmRecruitCandidateDO().setId(candidateId);
        candidate.setTenantId(100L);
        when(candidateService.validateRecruitCandidateExists(candidateId)).thenReturn(candidate);
        HrmRecruitResumeDO sameTenant = new HrmRecruitResumeDO().setCandidateId(candidateId)
                .setFileName("tenant-resume.pdf").setFileUrl("tenant-resume.pdf").setVersionNo(1).setStatus(0);
        sameTenant.setTenantId(100L);
        resumeMapper.insert(sameTenant);
        HrmRecruitResumeDO otherTenant = new HrmRecruitResumeDO().setCandidateId(candidateId)
                .setFileName("other-tenant-resume.pdf").setFileUrl("other-tenant-resume.pdf")
                .setVersionNo(2).setStatus(0);
        otherTenant.setTenantId(200L);
        resumeMapper.insert(otherTenant);

        List<HrmRecruitResumeDO> resumes = resumeService.getRecruitResumeList(candidateId);

        assertEquals(1, resumes.size());
        assertEquals("tenant-resume.pdf", resumes.get(0).getFileName());
    }
}
