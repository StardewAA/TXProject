package cn.iocoder.txgy.module.cms.service.Policy;

import cn.iocoder.txgy.module.cms.controller.admin.Policy.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Policy.CmsPolicyDO;
import cn.iocoder.txgy.module.cms.dal.mysql.Policy.CmsPolicyMapper;
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
 * 政策资讯 Service 实现类
 *
 * @author yuting_feng
 */
@Service
@Validated
public class CmsPolicyServiceImpl implements CmsPolicyService {

    @Resource
    private CmsPolicyMapper policyMapper;

    @Override
    public Long createPolicy(CmsPolicySaveReqVO createReqVO) {
        CmsPolicyDO record = BeanUtils.toBean(createReqVO, CmsPolicyDO.class);
        policyMapper.insert(record);
        return record.getPolicyId();
    }

    @Override
    public void updatePolicy(CmsPolicySaveReqVO updateReqVO) {
        validatePolicyExists(updateReqVO.getPolicyId());
        CmsPolicyDO updateObj = BeanUtils.toBean(updateReqVO, CmsPolicyDO.class);
        policyMapper.updateById(updateObj);
    }

    @Override
    public void deletePolicy(Long policyId) {
        validatePolicyExists(policyId);
        policyMapper.deleteById(policyId);
    }

    private void validatePolicyExists(Long policyId) {
        if (policyMapper.selectById(policyId) == null) {
            throw exception(POLICY_NOT_EXISTS);
        }
    }

    @Override
    public CmsPolicyDO getPolicy(Long policyId) {
        return policyMapper.selectById(policyId);
    }

    @Override
    public PageResult<CmsPolicyDO> getPolicyPage(CmsPolicyPageReqVO pageReqVO) {
        return policyMapper.selectPage(pageReqVO);
    }

    @Override
    public java.util.List<CmsPolicyDO> getPolicyList() {
        return policyMapper.selectList(new LambdaQueryWrapperX<CmsPolicyDO>()
                .orderByDesc(CmsPolicyDO::getPolicyId));
    }
}
