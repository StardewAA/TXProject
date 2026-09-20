package cn.iocoder.txgy.module.hrm.service.policy;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.policy.vo.HrmPolicyPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.policy.vo.HrmPolicySaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.policy.HrmPolicyDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.policy.HrmPolicyMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.HRM_POLICY_DATE_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.HRM_POLICY_NOT_EXISTS;

@Service
@Validated
public class HrmPolicyServiceImpl implements HrmPolicyService {

    @Resource
    private HrmPolicyMapper policyMapper;

    @Override
    public Long createPolicy(HrmPolicySaveReqVO reqVO) {
        validatePolicyDate(reqVO);
        HrmPolicyDO policy = BeanUtils.toBean(reqVO, HrmPolicyDO.class).setId(null);
        if (policy.getStatus() == null) {
            policy.setStatus(0);
        }
        if (policy.getCategory() == null) {
            policy.setCategory("other");
        }
        policyMapper.insert(policy);
        return policy.getId();
    }

    @Override
    public void updatePolicy(HrmPolicySaveReqVO reqVO) {
        validatePolicyExists(reqVO.getId());
        validatePolicyDate(reqVO);
        HrmPolicyDO updateObj = BeanUtils.toBean(reqVO, HrmPolicyDO.class);
        policyMapper.updateById(updateObj);
    }

    @Override
    public void deletePolicy(Long id) {
        validatePolicyExists(id);
        policyMapper.deleteById(id);
    }

    @Override
    public HrmPolicyDO getPolicy(Long id) {
        return policyMapper.selectById(id);
    }

    @Override
    public HrmPolicyDO validatePolicyExists(Long id) {
        if (id == null) {
            throw exception(HRM_POLICY_NOT_EXISTS);
        }
        HrmPolicyDO policy = policyMapper.selectById(id);
        if (policy == null) {
            throw exception(HRM_POLICY_NOT_EXISTS);
        }
        return policy;
    }

    @Override
    public PageResult<HrmPolicyDO> getPolicyPage(HrmPolicyPageReqVO reqVO) {
        return policyMapper.selectPage(reqVO);
    }

    /** 生效日期不能晚于失效日期 */
    private void validatePolicyDate(HrmPolicySaveReqVO reqVO) {
        LocalDate effectiveDate = reqVO.getEffectiveDate();
        LocalDate expireDate = reqVO.getExpireDate();
        if (effectiveDate != null && expireDate != null && effectiveDate.isAfter(expireDate)) {
            throw exception(HRM_POLICY_DATE_INVALID);
        }
    }
}
