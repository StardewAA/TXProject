package cn.iocoder.txgy.module.cms.service.Quota;

import cn.iocoder.txgy.module.cms.controller.admin.Quota.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Quota.CmsQuotaDO;
import cn.iocoder.txgy.module.cms.dal.mysql.Quota.CmsQuotaMapper;
import cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.*;

/**
 * 资质定额资料库 Service 实现类
 *
 * @author yuting_feng
 */
@Service
@Validated
public class CmsQuotaServiceImpl implements CmsQuotaService {

    @Resource
    private CmsQuotaMapper quotaMapper;

    @Override
    public Long createQuota(CmsQuotaSaveReqVO createReqVO) {
        CmsQuotaDO record = BeanUtils.toBean(createReqVO, CmsQuotaDO.class);
        quotaMapper.insert(record);
        return record.getQuotaId();
    }

    @Override
    public void updateQuota(CmsQuotaSaveReqVO updateReqVO) {
        validateQuotaExists(updateReqVO.getQuotaId());
        CmsQuotaDO updateObj = BeanUtils.toBean(updateReqVO, CmsQuotaDO.class);
        quotaMapper.updateById(updateObj);
    }

    @Override
    public void deleteQuota(Long quotaId) {
        validateQuotaExists(quotaId);
        quotaMapper.deleteById(quotaId);
    }

    private void validateQuotaExists(Long quotaId) {
        if (quotaMapper.selectById(quotaId) == null) {
            throw exception(QUOTA_NOT_EXISTS);
        }
    }

    @Override
    public CmsQuotaDO getQuota(Long quotaId) {
        return quotaMapper.selectById(quotaId);
    }

    @Override
    public PageResult<CmsQuotaDO> getQuotaPage(CmsQuotaPageReqVO pageReqVO) {
        return quotaMapper.selectPage(pageReqVO);
    }

    @Override
    public java.util.List<CmsQuotaDO> getQuotaList() {
        return quotaMapper.selectList(new LambdaQueryWrapperX<CmsQuotaDO>()
                .orderByDesc(CmsQuotaDO::getQuotaId));
    }
}
