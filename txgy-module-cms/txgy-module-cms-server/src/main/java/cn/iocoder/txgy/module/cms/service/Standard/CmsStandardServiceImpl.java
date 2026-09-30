package cn.iocoder.txgy.module.cms.service.Standard;

import cn.iocoder.txgy.module.cms.controller.admin.Standard.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Standard.CmsStandardDO;
import cn.iocoder.txgy.module.cms.dal.mysql.Standard.CmsStandardMapper;
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
 * 行业标准规范库 Service 实现类
 *
 * @author yuting_feng
 */
@Service
@Validated
public class CmsStandardServiceImpl implements CmsStandardService {

    @Resource
    private CmsStandardMapper standardMapper;

    @Override
    public Long createStandard(CmsStandardSaveReqVO createReqVO) {
        CmsStandardDO record = BeanUtils.toBean(createReqVO, CmsStandardDO.class);
        standardMapper.insert(record);
        return record.getStandardId();
    }

    @Override
    public void updateStandard(CmsStandardSaveReqVO updateReqVO) {
        validateStandardExists(updateReqVO.getStandardId());
        CmsStandardDO updateObj = BeanUtils.toBean(updateReqVO, CmsStandardDO.class);
        standardMapper.updateById(updateObj);
    }

    @Override
    public void deleteStandard(Long standardId) {
        validateStandardExists(standardId);
        standardMapper.deleteById(standardId);
    }

    private void validateStandardExists(Long standardId) {
        if (standardMapper.selectById(standardId) == null) {
            throw exception(STANDARD_NOT_EXISTS);
        }
    }

    @Override
    public CmsStandardDO getStandard(Long standardId) {
        return standardMapper.selectById(standardId);
    }

    @Override
    public PageResult<CmsStandardDO> getStandardPage(CmsStandardPageReqVO pageReqVO) {
        return standardMapper.selectPage(pageReqVO);
    }

    @Override
    public java.util.List<CmsStandardDO> getStandardList() {
        return standardMapper.selectList(new LambdaQueryWrapperX<CmsStandardDO>()
                .orderByDesc(CmsStandardDO::getStandardId));
    }
}
