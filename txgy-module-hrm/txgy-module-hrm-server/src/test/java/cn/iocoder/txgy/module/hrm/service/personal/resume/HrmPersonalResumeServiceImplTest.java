package cn.iocoder.txgy.module.hrm.service.personal.resume;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeUploadReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume.HrmPersonalResumeMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(HrmPersonalResumeServiceImpl.class)
public class HrmPersonalResumeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmPersonalResumeServiceImpl resumeService;
    @Resource
    private HrmPersonalResumeMapper resumeMapper;

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
}
