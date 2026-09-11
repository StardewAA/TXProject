package cn.iocoder.txgy.module.cms.service.Policy;

import cn.iocoder.txgy.module.cms.controller.admin.Policy.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Policy.CmsPolicyDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import jakarta.validation.Valid;

/**
 * 政策资讯 Service 接口
 *
 * @author yuting_feng
 */
public interface CmsPolicyService {

    Long createPolicy(@Valid CmsPolicySaveReqVO createReqVO);

    void updatePolicy(@Valid CmsPolicySaveReqVO updateReqVO);

    void deletePolicy(Long policyId);

    CmsPolicyDO getPolicy(Long policyId);

    PageResult<CmsPolicyDO> getPolicyPage(CmsPolicyPageReqVO pageReqVO);

    java.util.List<CmsPolicyDO> getPolicyList();
}
