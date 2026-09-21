package cn.iocoder.txgy.module.hrm.service.safety;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmSafetyEducationPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmSafetyEducationSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmSafetyEducationDO;

import java.util.List;

/**
 * HRM 三级安全教育 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmSafetyEducationService {

    Long createEducation(HrmSafetyEducationSaveReqVO reqVO);

    void updateEducation(HrmSafetyEducationSaveReqVO reqVO);

    void deleteEducation(Long id);

    HrmSafetyEducationDO validateEducationExists(Long id);

    PageResult<HrmSafetyEducationDO> getEducationPage(HrmSafetyEducationPageReqVO reqVO);

    List<HrmSafetyEducationDO> getEducationListByEmployeeId(Long employeeId);

}
