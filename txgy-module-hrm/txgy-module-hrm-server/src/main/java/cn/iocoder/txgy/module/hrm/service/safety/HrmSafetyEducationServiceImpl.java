package cn.iocoder.txgy.module.hrm.service.safety;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmSafetyEducationPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmSafetyEducationSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmSafetyEducationDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.safety.HrmSafetyEducationMapper;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SAFETY_EDUCATION_NOT_EXISTS;

/**
 * HRM 三级安全教育 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmSafetyEducationServiceImpl implements HrmSafetyEducationService {

    @Resource
    private HrmSafetyEducationMapper educationMapper;
    @Resource
    private HrmEmployeeService employeeService;

    @Override
    public Long createEducation(HrmSafetyEducationSaveReqVO reqVO) {
        employeeService.validateEmployeeExists(reqVO.getEmployeeId());
        HrmSafetyEducationDO education = BeanUtils.toBean(reqVO, HrmSafetyEducationDO.class);
        if (education.getQualified() == null) {
            education.setQualified(false);
        }
        educationMapper.insert(education);
        return education.getId();
    }

    @Override
    public void updateEducation(HrmSafetyEducationSaveReqVO reqVO) {
        validateEducationExists(reqVO.getId());
        educationMapper.updateById(BeanUtils.toBean(reqVO, HrmSafetyEducationDO.class));
    }

    @Override
    public void deleteEducation(Long id) {
        validateEducationExists(id);
        educationMapper.deleteById(id);
    }

    @Override
    public HrmSafetyEducationDO validateEducationExists(Long id) {
        HrmSafetyEducationDO education = educationMapper.selectById(id);
        if (education == null) {
            throw exception(SAFETY_EDUCATION_NOT_EXISTS);
        }
        return education;
    }

    @Override
    public PageResult<HrmSafetyEducationDO> getEducationPage(HrmSafetyEducationPageReqVO reqVO) {
        return educationMapper.selectPage(reqVO);
    }

    @Override
    public List<HrmSafetyEducationDO> getEducationListByEmployeeId(Long employeeId) {
        return educationMapper.selectListByEmployeeId(employeeId);
    }

}
