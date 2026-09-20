package cn.iocoder.txgy.module.hrm.service.employment;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employment.vo.HrmEmploymentTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employment.vo.HrmEmploymentTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employment.HrmEmploymentTypeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employment.HrmEmploymentTypeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYMENT_TYPE_CODE_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYMENT_TYPE_DISABLED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYMENT_TYPE_NAME_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYMENT_TYPE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYMENT_TYPE_TEMPLATE_REQUIRED;

/**
 * HRM 用工形式 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmEmploymentTypeServiceImpl implements HrmEmploymentTypeService {

    @Resource
    private HrmEmploymentTypeMapper employmentTypeMapper;

    @Override
    public Long createEmploymentType(HrmEmploymentTypeSaveReqVO reqVO) {
        // 1. 名称与编码唯一
        if (employmentTypeMapper.selectByName(reqVO.getName()) != null) {
            throw exception(EMPLOYMENT_TYPE_NAME_DUPLICATE);
        }
        if (employmentTypeMapper.selectByCode(reqVO.getCode()) != null) {
            throw exception(EMPLOYMENT_TYPE_CODE_DUPLICATE);
        }
        // 2. 插入
        HrmEmploymentTypeDO employmentType = BeanUtils.toBean(reqVO, HrmEmploymentTypeDO.class);
        employmentTypeMapper.insert(employmentType);
        return employmentType.getId();
    }

    @Override
    public void updateEmploymentType(HrmEmploymentTypeSaveReqVO reqVO) {
        // 1. 校验存在
        validateEmploymentTypeExists(reqVO.getId());
        // 2. 名称与编码唯一（排除自身）
        HrmEmploymentTypeDO byName = employmentTypeMapper.selectByName(reqVO.getName());
        if (byName != null && !byName.getId().equals(reqVO.getId())) {
            throw exception(EMPLOYMENT_TYPE_NAME_DUPLICATE);
        }
        HrmEmploymentTypeDO byCode = employmentTypeMapper.selectByCode(reqVO.getCode());
        if (byCode != null && !byCode.getId().equals(reqVO.getId())) {
            throw exception(EMPLOYMENT_TYPE_CODE_DUPLICATE);
        }
        // 3. 更新
        employmentTypeMapper.updateById(BeanUtils.toBean(reqVO, HrmEmploymentTypeDO.class));
    }

    @Override
    public void deleteEmploymentType(Long id) {
        validateEmploymentTypeExists(id);
        employmentTypeMapper.deleteById(id);
    }

    @Override
    public HrmEmploymentTypeDO getEmploymentType(Long id) {
        return employmentTypeMapper.selectById(id);
    }

    @Override
    public HrmEmploymentTypeDO validateEmploymentTypeExists(Long id) {
        HrmEmploymentTypeDO employmentType = employmentTypeMapper.selectById(id);
        if (employmentType == null) {
            throw exception(EMPLOYMENT_TYPE_NOT_EXISTS);
        }
        return employmentType;
    }

    @Override
    public List<HrmEmploymentTypeDO> getEmploymentTypeList() {
        return employmentTypeMapper.selectListAll();
    }

    @Override
    public List<HrmEmploymentTypeDO> getEnabledEmploymentTypeList() {
        return employmentTypeMapper.selectListByStatus(CommonStatusEnum.ENABLE.getStatus());
    }

    @Override
    public PageResult<HrmEmploymentTypeDO> getEmploymentTypePage(HrmEmploymentTypePageReqVO reqVO) {
        return employmentTypeMapper.selectPage(reqVO);
    }

    @Override
    public HrmEmploymentTypeDO validateUsableForOnboard(Long id) {
        HrmEmploymentTypeDO employmentType = validateEmploymentTypeExists(id);
        // 停用后历史员工不受影响，但新转员工不允许再选
        if (!CommonStatusEnum.ENABLE.getStatus().equals(employmentType.getStatus())) {
            throw exception(EMPLOYMENT_TYPE_DISABLED, employmentType.getName());
        }
        // 未绑定合同模板不允许转员工：合同与计薪都由用工形式决定，缺一项都不算闭环
        if (employmentType.getContractTemplateId() == null) {
            throw exception(EMPLOYMENT_TYPE_TEMPLATE_REQUIRED, employmentType.getName());
        }
        return employmentType;
    }

}
