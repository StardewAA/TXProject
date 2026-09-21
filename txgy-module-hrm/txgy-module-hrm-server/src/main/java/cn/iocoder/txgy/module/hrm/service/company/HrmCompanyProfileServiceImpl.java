package cn.iocoder.txgy.module.hrm.service.company;

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.company.vo.HrmCompanyProfileSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.company.HrmCompanyProfileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.COMPANY_PROFILE_NOT_EXISTS;

/**
 * HRM 公司信息（用工主体）Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmCompanyProfileServiceImpl implements HrmCompanyProfileService {

    @Resource
    private HrmCompanyProfileMapper companyProfileMapper;

    @Override
    public Long saveProfile(HrmCompanyProfileSaveReqVO reqVO) {
        HrmCompanyProfileDO profile = BeanUtils.toBean(reqVO, HrmCompanyProfileDO.class);
        if (reqVO.getId() == null) {
            companyProfileMapper.insert(profile);
        } else {
            validateProfileExists(reqVO.getId());
            companyProfileMapper.updateById(profile);
        }
        // 默认主体唯一：勾选后取消其它主体的默认标记
        if (Boolean.TRUE.equals(reqVO.getDefaultStatus())) {
            companyProfileMapper.selectList().stream()
                    .filter(item -> !item.getId().equals(profile.getId()))
                    .filter(item -> Boolean.TRUE.equals(item.getDefaultStatus()))
                    .forEach(item -> companyProfileMapper.updateById(
                            new HrmCompanyProfileDO().setId(item.getId()).setDefaultStatus(false)));
        }
        return profile.getId();
    }

    @Override
    public void deleteProfile(Long id) {
        validateProfileExists(id);
        companyProfileMapper.deleteById(id);
    }

    @Override
    public HrmCompanyProfileDO validateProfileExists(Long id) {
        HrmCompanyProfileDO profile = companyProfileMapper.selectById(id);
        if (profile == null) {
            throw exception(COMPANY_PROFILE_NOT_EXISTS);
        }
        return profile;
    }

    @Override
    public List<HrmCompanyProfileDO> getProfileList() {
        return companyProfileMapper.selectList();
    }

    @Override
    public HrmCompanyProfileDO getDefaultProfile() {
        return companyProfileMapper.selectDefaultProfile();
    }

}
