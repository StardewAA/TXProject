package cn.iocoder.txgy.module.hrm.service.recruit.company;

import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company.HrmRecruitCompanyProfileSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.company.HrmRecruitCompanyProfileDO;

public interface HrmRecruitCompanyProfileService {

    HrmRecruitCompanyProfileDO getCurrentTenantProfile();

    HrmRecruitCompanyProfileDO getPortalProfile(Long tenantId);

    Long saveCurrentTenantProfile(HrmRecruitCompanyProfileSaveReqVO reqVO);
}
