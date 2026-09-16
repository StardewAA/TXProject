package cn.iocoder.txgy.module.hrm.service.personal.resume;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeUploadReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.candidate.HrmRecruitCandidateMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume.HrmPersonalResumeMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(HrmPersonalResumeServiceImpl.class)
public class HrmPersonalResumeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmPersonalResumeServiceImpl resumeService;
    @Resource
    private HrmPersonalResumeMapper resumeMapper;
    @Resource
    private HrmRecruitApplicationMapper applicationMapper;
    @Resource
    private HrmRecruitCandidateMapper candidateMapper;

    @Test
    public void testCreateUploadResume_savesAttachmentForUser() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long id;
        try {
            id = resumeService.createUploadResume(userId, new HrmPortalResumeUploadReqVO()
                    .setTitle("我的附件简历")
                    .setFileName("resume.pdf")
                    .setFileUrl("https://files.example/resume.pdf")
                    .setRemark("投递通用版本"));
        } finally {
            TenantContextHolder.clear();
        }

        HrmPersonalResumeDO saved = TenantUtils.executeIgnore(() -> resumeMapper.selectByIdAndUserId(id, userId));
        assertNotNull(saved);
        assertEquals(101L, saved.getTenantId());
        assertEquals(1, saved.getResumeType());
        assertEquals("resume.pdf", saved.getFileName());
        assertEquals("https://files.example/resume.pdf", saved.getFileUrl());
    }

    @Test
    public void testCreateOnlineResume_withoutCompanyUsesPublicTenant() {
        Long userId = randomLongId();
        TenantContextHolder.clear();

        Long id = resumeService.createOnlineResume(userId, new HrmPortalResumeCreateReqVO()
                .setTitle("我的在线简历")
                .setContent("教育经历和工作经历占位内容")
                .setRemark("在线填写"));

        HrmPersonalResumeDO saved = TenantUtils.executeIgnore(() -> resumeMapper.selectByIdAndUserId(id, userId));
        assertNotNull(saved);
        assertEquals(0L, saved.getTenantId());
        assertEquals(2, saved.getResumeType());
        assertEquals("教育经历和工作经历占位内容", saved.getContent());
    }

    @Test
    public void testCreateOnlineResume_updatesRelatedCandidatesAndReusesProfile() {
        Long userId = randomLongId();
        HrmRecruitCandidateDO candidate = new HrmRecruitCandidateDO()
                .setName("旧姓名").setMobile("13800000000").setSex(1).setPostId(randomLongId())
                .setEducation(5);
        candidate.setTenantId(101L);
        HrmRecruitApplicationDO application = new HrmRecruitApplicationDO()
                .setApplicantUserId(userId).setCandidateId(candidate.getId()).setPostId(candidate.getPostId())
                .setResumeId(randomLongId()).setStatus(HrmRecruitApplicationStatusEnum.SUBMITTED.getStatus())
                .setStatusUpdateTime(LocalDateTime.now()).setVersion(0);
        application.setTenantId(101L);
        TenantContextHolder.setTenantId(101L);
        try {
            candidateMapper.insert(candidate);
            application.setCandidateId(candidate.getId());
            applicationMapper.insert(application);
        } finally {
            TenantContextHolder.clear();
        }

        TenantContextHolder.setTenantId(999L);
        Long firstResumeId;
        Long secondResumeId;
        try {
            firstResumeId = resumeService.createOnlineResume(userId, new HrmPortalResumeCreateReqVO()
                    .setTitle("求职资料（系统）")
                    .setContent("{\"name\":\"张三\",\"mobile\":\"13900000000\",\"gender\":2,"
                            + "\"birthMonth\":\"1995-01\",\"email\":\"zhangsan@example.com\","
                            + "\"education\":\"本科\",\"school\":\"测试大学\",\"company\":\"测试公司\","
                            + "\"jobStart\":\"2020-01\",\"jobEnd\":\"\"}"));
            secondResumeId = resumeService.createOnlineResume(userId, new HrmPortalResumeCreateReqVO()
                    .setTitle("求职资料（系统）")
                    .setContent("{\"name\":\"李四\",\"mobile\":\"13900000001\",\"gender\":1,"
                            + "\"birthMonth\":\"1996-02\",\"email\":\"lisi@example.com\","
                            + "\"education\":\"硕士\",\"school\":\"新测试大学\",\"company\":\"新测试公司\","
                            + "\"jobStart\":\"2021-03\",\"jobEnd\":\"\"}"));
        } finally {
            TenantContextHolder.clear();
        }

        HrmRecruitCandidateDO savedCandidate = TenantUtils.executeIgnore(
                () -> candidateMapper.selectById(candidate.getId()));
        List<HrmPersonalResumeDO> profiles = resumeService.getResumeList(userId).stream()
                .filter(item -> "求职资料（系统）".equals(item.getTitle()))
                .toList();
        assertEquals(firstResumeId, secondResumeId);
        assertEquals(1, profiles.size());
        assertEquals("李四", savedCandidate.getName());
        assertEquals("13900000001", savedCandidate.getMobile());
        assertEquals(1, savedCandidate.getSex());
        assertEquals("lisi@example.com", savedCandidate.getEmail());
        assertEquals(6, savedCandidate.getEducation());
        assertEquals("新测试大学", savedCandidate.getGraduateSchool());
        assertEquals("新测试公司", savedCandidate.getLatestWorkPlace());
        assertEquals(5, savedCandidate.getWorkTime());
    }

    @Test
    public void testGetResumeList_onlyReturnsCurrentUserRecordsAcrossTenants() {
        Long userId = randomLongId();
        Long otherUserId = randomLongId();
        TenantContextHolder.setTenantId(202L);
        try {
            resumeService.createOnlineResume(userId, new HrmPortalResumeCreateReqVO().setTitle("在线简历"));
        } finally {
            TenantContextHolder.clear();
        }
        TenantContextHolder.setTenantId(303L);
        try {
            resumeService.createUploadResume(otherUserId, new HrmPortalResumeUploadReqVO()
                    .setTitle("其他用户简历").setFileName("other.docx").setFileUrl("https://files.example/other.docx"));
        } finally {
            TenantContextHolder.clear();
        }

        List<HrmPersonalResumeDO> list = resumeService.getResumeList(userId);
        assertEquals(1, list.size());
        assertEquals(userId, list.get(0).getUserId());
        assertTrue(resumeService.getResumeList(otherUserId).stream()
                .allMatch(item -> otherUserId.equals(item.getUserId())));
    }

    @Test
    public void testDeleteUploadResume_onlyDeletesCurrentUserAttachment() {
        Long userId = randomLongId();
        Long otherUserId = randomLongId();
        TenantContextHolder.setTenantId(0L);
        Long uploadId;
        Long onlineId;
        Long otherUploadId;
        try {
            uploadId = resumeService.createUploadResume(userId, new HrmPortalResumeUploadReqVO()
                    .setTitle("待删除简历")
                    .setFileName("delete.pdf")
                    .setFileUrl("https://files.example/delete.pdf"));
            onlineId = resumeService.createOnlineResume(userId, new HrmPortalResumeCreateReqVO()
                    .setTitle("在线简历")
                    .setContent("在线资料"));
            otherUploadId = resumeService.createUploadResume(otherUserId, new HrmPortalResumeUploadReqVO()
                    .setTitle("其他用户简历")
                    .setFileName("other.pdf")
                    .setFileUrl("https://files.example/other.pdf"));
        } finally {
            TenantContextHolder.clear();
        }

        assertTrue(resumeService.deleteUploadResume(userId, uploadId));
        assertNull(TenantUtils.executeIgnore(() -> resumeMapper.selectByIdAndUserId(uploadId, userId)));
        assertNotNull(TenantUtils.executeIgnore(() -> resumeMapper.selectByIdAndUserId(onlineId, userId)));
        assertFalse(resumeService.deleteUploadResume(userId, otherUploadId));
        assertNotNull(TenantUtils.executeIgnore(() -> resumeMapper.selectByIdAndUserId(otherUploadId, otherUserId)));
    }
}
