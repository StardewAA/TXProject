package cn.iocoder.txgy.module.hrm.service.personal.resume;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeVersionCreateReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume.HrmPersonalResumeMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume.HrmPersonalResumeVersionMapper;
import cn.iocoder.txgy.module.hrm.util.HrmResumeFingerprintUtils;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.PERSONAL_RESUME_VERSION_CONTENT_EMPTY;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.PERSONAL_RESUME_VERSION_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.PERSONAL_RESUME_VERSION_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.PERSONAL_RESUME_VERSION_PARENT_INVALID;

@Service
@Validated
public class HrmPersonalResumeVersionServiceImpl implements HrmPersonalResumeVersionService {

    private static final String DEFAULT_SCHEMA_VERSION = "v1";

    @Resource
    private HrmPersonalResumeVersionMapper versionMapper;
    @Resource
    private HrmPersonalResumeMapper resumeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVersion(Long userId, HrmPortalResumeVersionCreateReqVO reqVO) {
        if (userId == null) {
            throw exception(PERSONAL_RESUME_VERSION_NOT_EXISTS);
        }
        // 幂等：同一用户同一幂等键直接返回已有版本，避免重复点击生成多份
        HrmPersonalResumeVersionDO existing = TenantUtils.executeIgnore(
                () -> versionMapper.selectByUserIdAndIdempotencyKey(userId, reqVO.getIdempotencyKey()));
        if (existing != null) {
            return existing.getId();
        }
        // 内容来源：优先使用请求显式传入的最终值；否则从来源草稿固化
        String content = StrUtil.isNotBlank(reqVO.getContent())
                ? reqVO.getContent() : resolveDraftContent(userId, reqVO.getSourceResumeId());
        if (StrUtil.isBlank(content)) {
            throw exception(PERSONAL_RESUME_VERSION_CONTENT_EMPTY);
        }
        // 父版本必须是本人的版本，避免通过父版本越权引用他人数据
        HrmPersonalResumeVersionDO parent = null;
        if (reqVO.getParentVersionId() != null) {
            parent = TenantUtils.executeIgnore(
                    () -> versionMapper.selectByIdAndUserId(reqVO.getParentVersionId(), userId));
            if (parent == null) {
                throw exception(PERSONAL_RESUME_VERSION_PARENT_INVALID);
            }
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            tenantId = 0L;
        }
        Integer maxVersionNo = TenantUtils.executeIgnore(() -> versionMapper.selectMaxVersionNoByUserId(userId));
        HrmPersonalResumeVersionDO version = new HrmPersonalResumeVersionDO()
                .setUserId(userId)
                .setParentVersionId(parent == null ? null : parent.getId())
                .setVersionNo(maxVersionNo == null ? 1 : maxVersionNo + 1)
                .setSourceResumeId(reqVO.getSourceResumeId())
                .setSourcePostId(reqVO.getSourcePostId())
                .setCreateType(reqVO.getCreateType() == null
                        ? HrmPersonalResumeVersionDO.CREATE_TYPE_MANUAL : reqVO.getCreateType())
                .setChangeSummary(reqVO.getChangeSummary())
                .setSchemaVersion(DEFAULT_SCHEMA_VERSION)
                .setContent(content)
                .setContentHash(HrmResumeFingerprintUtils.fingerprintResumeContent(content))
                .setIdempotencyKey(reqVO.getIdempotencyKey())
                .setStatus(HrmPersonalResumeVersionDO.STATUS_ACTIVE);
        version.setTenantId(tenantId);
        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException duplicate) {
            // 并发下两个请求同时通过幂等预检，唯一键兜底，返回先写入的版本
            HrmPersonalResumeVersionDO concurrent = TenantUtils.executeIgnore(
                    () -> versionMapper.selectByUserIdAndIdempotencyKey(userId, reqVO.getIdempotencyKey()));
            if (concurrent != null) {
                return concurrent.getId();
            }
            throw exception(PERSONAL_RESUME_VERSION_IDEMPOTENCY_CONFLICT);
        }
        return version.getId();
    }

    @Override
    public List<HrmPersonalResumeVersionDO> getVersionList(Long userId, Long sourceResumeId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return TenantUtils.executeIgnore(() -> versionMapper.selectListByUserId(userId, sourceResumeId));
    }

    @Override
    public HrmPersonalResumeVersionDO getVersion(Long userId, Long id) {
        if (userId == null || id == null) {
            throw exception(PERSONAL_RESUME_VERSION_NOT_EXISTS);
        }
        HrmPersonalResumeVersionDO version = TenantUtils.executeIgnore(
                () -> versionMapper.selectByIdAndUserId(id, userId));
        if (version == null) {
            throw exception(PERSONAL_RESUME_VERSION_NOT_EXISTS);
        }
        return version;
    }

    /** 从来源草稿读取在线简历内容，并校验草稿归属当前用户 */
    private String resolveDraftContent(Long userId, Long sourceResumeId) {
        if (sourceResumeId == null) {
            return null;
        }
        HrmPersonalResumeDO draft = TenantUtils.executeIgnore(
                () -> resumeMapper.selectByIdAndUserId(sourceResumeId, userId));
        return draft == null ? null : draft.getContent();
    }
}
