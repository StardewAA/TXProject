package cn.iocoder.txgy.module.hrm.service.personal.resume;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeUploadReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume.HrmPersonalResumeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;

@Service
@Validated
public class HrmPersonalResumeServiceImpl implements HrmPersonalResumeService {

    private static final int UPLOAD_RESUME_TYPE = 1;
    private static final int ONLINE_RESUME_TYPE = 2;

    @Resource
    private HrmPersonalResumeMapper resumeMapper;

    @Override
    public Long createUploadResume(Long userId, HrmPortalResumeUploadReqVO reqVO) {
        HrmPersonalResumeDO resume = BeanUtils.toBean(reqVO, HrmPersonalResumeDO.class)
                .setUserId(userId).setResumeType(UPLOAD_RESUME_TYPE);
        return insertInCurrentTenant(resume);
    }

    @Override
    public Long createOnlineResume(Long userId, HrmPortalResumeCreateReqVO reqVO) {
        HrmPersonalResumeDO resume = BeanUtils.toBean(reqVO, HrmPersonalResumeDO.class)
                .setUserId(userId).setResumeType(ONLINE_RESUME_TYPE);
        return insertInCurrentTenant(resume);
    }

    @Override
    public List<HrmPersonalResumeDO> getResumeList(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return TenantUtils.executeIgnore(() -> resumeMapper.selectListByUserId(userId));
    }

    @Override
    public HrmPersonalResumeDO getResume(Long userId, Long id) {
        if (userId == null || id == null) {
            return null;
        }
        return TenantUtils.executeIgnore(() -> resumeMapper.selectByIdAndUserId(id, userId));
    }

    private Long insertInCurrentTenant(HrmPersonalResumeDO resume) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            tenantId = 0L;
        }
        Long targetTenantId = tenantId;
        resume.setTenantId(targetTenantId);
        final Long[] id = new Long[1];
        TenantUtils.execute(targetTenantId, () -> {
            resumeMapper.insert(resume);
            id[0] = resume.getId();
        });
        return id[0];
    }
}
