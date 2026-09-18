package cn.iocoder.txgy.module.cms.service.ProcessResource;

import cn.iocoder.txgy.module.cms.controller.admin.ProcessResource.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.ProcessResource.CmsProcessResourceDO;
import cn.iocoder.txgy.module.cms.dal.mysql.ProcessResource.CmsProcessResourceMapper;
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
 * 工艺工法与案例库 Service 实现类
 *
 * @author yuting_feng
 */
@Service
@Validated
public class CmsProcessResourceServiceImpl implements CmsProcessResourceService {

    @Resource
    private CmsProcessResourceMapper processMapper;

    @Override
    public Long createProcessResource(CmsProcessResourceSaveReqVO createReqVO) {
        CmsProcessResourceDO record = BeanUtils.toBean(createReqVO, CmsProcessResourceDO.class);
        processMapper.insert(record);
        return record.getResourceId();
    }

    @Override
    public void updateProcessResource(CmsProcessResourceSaveReqVO updateReqVO) {
        validateProcessResourceExists(updateReqVO.getResourceId());
        CmsProcessResourceDO updateObj = BeanUtils.toBean(updateReqVO, CmsProcessResourceDO.class);
        processMapper.updateById(updateObj);
    }

    @Override
    public void deleteProcessResource(Long resourceId) {
        validateProcessResourceExists(resourceId);
        processMapper.deleteById(resourceId);
    }

    private void validateProcessResourceExists(Long resourceId) {
        if (processMapper.selectById(resourceId) == null) {
            throw exception(PROCESS_NOT_EXISTS);
        }
    }

    @Override
    public CmsProcessResourceDO getProcessResource(Long resourceId) {
        return processMapper.selectById(resourceId);
    }

    @Override
    public PageResult<CmsProcessResourceDO> getProcessResourcePage(CmsProcessResourcePageReqVO pageReqVO) {
        return processMapper.selectPage(pageReqVO);
    }

    @Override
    public java.util.List<CmsProcessResourceDO> getProcessResourceList() {
        return processMapper.selectList(new LambdaQueryWrapperX<CmsProcessResourceDO>()
                .orderByDesc(CmsProcessResourceDO::getResourceId));
    }
}
