package cn.iocoder.txgy.module.hrm.service.personal.resume;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeVersionCreateReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume.HrmPersonalResumeMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume.HrmPersonalResumeVersionMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.PERSONAL_RESUME_VERSION_CONTENT_EMPTY;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.PERSONAL_RESUME_VERSION_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.PERSONAL_RESUME_VERSION_PARENT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 个人简历版本服务测试。
 *
 * <p>重点验证：内容从草稿固化、版本号递增、幂等不重复生成、父版本与版本的归属校验。</p>
 */
@Import(HrmPersonalResumeVersionServiceImpl.class)
public class HrmPersonalResumeVersionServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmPersonalResumeVersionServiceImpl versionService;
    @Resource
    private HrmPersonalResumeVersionMapper versionMapper;
    @Resource
    private HrmPersonalResumeMapper resumeMapper;

    private static final String PROFILE_JSON = "{\"name\":\"张三\",\"mobile\":\"13900000000\"}";

    @Test
    public void testCreateVersion_fromDraftContent() {
        Long userId = randomLongId();
        // 先写入一条在线简历草稿，再由版本服务固化
        HrmPersonalResumeDO draft = new HrmPersonalResumeDO()
                .setUserId(userId).setTitle("求职资料（系统）").setResumeType(2).setContent(PROFILE_JSON);
        draft.setTenantId(101L);
        TenantUtils.execute(101L, () -> resumeMapper.insert(draft));

        TenantContextHolder.setTenantId(101L);
        Long versionId;
        try {
            versionId = versionService.createVersion(userId, new HrmPortalResumeVersionCreateReqVO()
                    .setSourceResumeId(draft.getId()).setCreateType(1)
                    .setChangeSummary("首次固化").setIdempotencyKey("key-1"));
        } finally {
            TenantContextHolder.clear();
        }

        HrmPersonalResumeVersionDO version = TenantUtils.executeIgnore(
                () -> versionMapper.selectByIdAndUserId(versionId, userId));
        assertNotNull(version);
        assertEquals(1, version.getVersionNo());
        assertEquals(PROFILE_JSON, version.getContent());
        assertNotNull(version.getContentHash());
        assertEquals(draft.getId(), version.getSourceResumeId());
        assertEquals("首次固化", version.getChangeSummary());
        assertNull(version.getParentVersionId());
        assertEquals(HrmPersonalResumeVersionDO.STATUS_ACTIVE, version.getStatus());
    }

    @Test
    public void testCreateVersion_isIdempotentForSameKey() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long first;
        Long second;
        try {
            first = versionService.createVersion(userId, new HrmPortalResumeVersionCreateReqVO()
                    .setContent(PROFILE_JSON).setIdempotencyKey("dup-key"));
            second = versionService.createVersion(userId, new HrmPortalResumeVersionCreateReqVO()
                    .setContent(PROFILE_JSON).setIdempotencyKey("dup-key"));
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(first, second);
        List<HrmPersonalResumeVersionDO> list = versionService.getVersionList(userId, null);
        assertEquals(1, list.size());
    }

    @Test
    public void testCreateVersion_allocatesIncreasingVersionNo() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long first;
        Long second;
        try {
            first = versionService.createVersion(userId, new HrmPortalResumeVersionCreateReqVO()
                    .setContent(PROFILE_JSON).setIdempotencyKey("v-1"));
            second = versionService.createVersion(userId, new HrmPortalResumeVersionCreateReqVO()
                    .setContent("{\"name\":\"李四\"}").setParentVersionId(first)
                    .setIdempotencyKey("v-2"));
        } finally {
            TenantContextHolder.clear();
        }

        HrmPersonalResumeVersionDO firstVersion = TenantUtils.executeIgnore(
                () -> versionMapper.selectByIdAndUserId(first, userId));
        HrmPersonalResumeVersionDO secondVersion = TenantUtils.executeIgnore(
                () -> versionMapper.selectByIdAndUserId(second, userId));
        assertNotEquals(first, second);
        assertEquals(1, firstVersion.getVersionNo());
        assertEquals(2, secondVersion.getVersionNo());
        assertEquals(first, secondVersion.getParentVersionId());
    }

    @Test
    public void testCreateVersion_rejectsEmptyContent() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        try {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> versionService.createVersion(userId, new HrmPortalResumeVersionCreateReqVO()
                            .setIdempotencyKey("empty-key")));
            assertEquals(PERSONAL_RESUME_VERSION_CONTENT_EMPTY.getCode(), exception.getCode());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    public void testCreateVersion_rejectsParentOfOtherUser() {
        Long ownerId = randomLongId();
        Long otherUserId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long ownerVersionId;
        try {
            ownerVersionId = versionService.createVersion(ownerId, new HrmPortalResumeVersionCreateReqVO()
                    .setContent(PROFILE_JSON).setIdempotencyKey("owner-key"));
        } finally {
            TenantContextHolder.clear();
        }

        TenantContextHolder.setTenantId(101L);
        try {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> versionService.createVersion(otherUserId, new HrmPortalResumeVersionCreateReqVO()
                            .setContent(PROFILE_JSON).setParentVersionId(ownerVersionId)
                            .setIdempotencyKey("other-key")));
            assertEquals(PERSONAL_RESUME_VERSION_PARENT_INVALID.getCode(), exception.getCode());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    public void testGetVersionList_onlyReturnsOwnVersions() {
        Long userId = randomLongId();
        Long otherUserId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        try {
            versionService.createVersion(userId, new HrmPortalResumeVersionCreateReqVO()
                    .setContent(PROFILE_JSON).setIdempotencyKey("mine"));
            versionService.createVersion(otherUserId, new HrmPortalResumeVersionCreateReqVO()
                    .setContent(PROFILE_JSON).setIdempotencyKey("theirs"));
        } finally {
            TenantContextHolder.clear();
        }

        List<HrmPersonalResumeVersionDO> mine = versionService.getVersionList(userId, null);
        assertEquals(1, mine.size());
        assertTrue(mine.stream().allMatch(item -> userId.equals(item.getUserId())));
    }

    @Test
    public void testGetVersion_rejectsOtherUserVersion() {
        Long ownerId = randomLongId();
        Long otherUserId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long versionId;
        try {
            versionId = versionService.createVersion(ownerId, new HrmPortalResumeVersionCreateReqVO()
                    .setContent(PROFILE_JSON).setIdempotencyKey("private-key"));
        } finally {
            TenantContextHolder.clear();
        }

        ServiceException exception = assertThrows(ServiceException.class,
                () -> versionService.getVersion(otherUserId, versionId));
        assertEquals(PERSONAL_RESUME_VERSION_NOT_EXISTS.getCode(), exception.getCode());
    }
}
