package cn.iocoder.txgy.module.hrm.service.policy;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.policy.vo.HrmPolicyPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.policy.vo.HrmPolicySaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.policy.HrmPolicyDO;

public interface HrmPolicyService {

    Long createPolicy(HrmPolicySaveReqVO reqVO);

    void updatePolicy(HrmPolicySaveReqVO reqVO);

    void deletePolicy(Long id);

    HrmPolicyDO getPolicy(Long id);

    HrmPolicyDO validatePolicyExists(Long id);

    PageResult<HrmPolicyDO> getPolicyPage(HrmPolicyPageReqVO reqVO);
}
