package cn.iocoder.txgy.module.hrm.service.recruit.company;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company.HrmRecruitCompanyProfileSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.company.HrmRecruitCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.company.HrmRecruitCompanyProfileMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class HrmRecruitCompanyProfileServiceImpl implements HrmRecruitCompanyProfileService {

    @Resource
    private HrmRecruitCompanyProfileMapper companyProfileMapper;

    @Override
    public HrmRecruitCompanyProfileDO getCurrentTenantProfile() {
        return companyProfileMapper.selectByTenantId(TenantContextHolder.getTenantId());
    }

    @Override
    public HrmRecruitCompanyProfileDO getPortalProfile(Long tenantId) {
        return TenantUtils.executeIgnore(() -> companyProfileMapper.selectPortalByTenantId(tenantId));
    }

    @Override
    public Long saveCurrentTenantProfile(HrmRecruitCompanyProfileSaveReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        HrmRecruitCompanyProfileDO current = companyProfileMapper.selectByTenantId(tenantId);
        HrmRecruitCompanyProfileDO profile = BeanUtils.toBean(reqVO, HrmRecruitCompanyProfileDO.class);
        profile.setTenantId(tenantId);
        profile.setVerified(Boolean.TRUE.equals(reqVO.getVerified()));
        if (reqVO.getStatus() == null) {
            profile.setStatus(0);
        }
        if (current == null) {
            companyProfileMapper.insert(profile);
            return profile.getId();
        }
        profile.setId(current.getId());
        LambdaUpdateWrapper<HrmRecruitCompanyProfileDO> update = new LambdaUpdateWrapper<HrmRecruitCompanyProfileDO>()
                .eq(HrmRecruitCompanyProfileDO::getId, current.getId())
                .eq(HrmRecruitCompanyProfileDO::getTenantId, tenantId)
                .set(HrmRecruitCompanyProfileDO::getName, profile.getName())
                .set(HrmRecruitCompanyProfileDO::getShortName, profile.getShortName())
                .set(HrmRecruitCompanyProfileDO::getLogo, profile.getLogo())
                .set(HrmRecruitCompanyProfileDO::getIndustry, profile.getIndustry())
                .set(HrmRecruitCompanyProfileDO::getScale, profile.getScale())
                .set(HrmRecruitCompanyProfileDO::getFinancingStage, profile.getFinancingStage())
                .set(HrmRecruitCompanyProfileDO::getNature, profile.getNature())
                .set(HrmRecruitCompanyProfileDO::getSummary, profile.getSummary())
                .set(HrmRecruitCompanyProfileDO::getProducts, profile.getProducts())
                .set(HrmRecruitCompanyProfileDO::getAlbum, profile.getAlbum())
                .set(HrmRecruitCompanyProfileDO::getAddress, profile.getAddress())
                .set(HrmRecruitCompanyProfileDO::getAreaId, profile.getAreaId())
                .set(HrmRecruitCompanyProfileDO::getLongitude, profile.getLongitude())
                .set(HrmRecruitCompanyProfileDO::getLatitude, profile.getLatitude())
                .set(HrmRecruitCompanyProfileDO::getWebsite, profile.getWebsite())
                .set(HrmRecruitCompanyProfileDO::getEstablishedDate, profile.getEstablishedDate())
                .set(HrmRecruitCompanyProfileDO::getRegisteredCapital, profile.getRegisteredCapital())
                .set(HrmRecruitCompanyProfileDO::getCreditCode, profile.getCreditCode())
                .set(HrmRecruitCompanyProfileDO::getLegalPerson, profile.getLegalPerson())
                .set(HrmRecruitCompanyProfileDO::getDataSource, profile.getDataSource())
                .set(HrmRecruitCompanyProfileDO::getDataSourceUrl, profile.getDataSourceUrl())
                .set(HrmRecruitCompanyProfileDO::getVerified, profile.getVerified())
                .set(HrmRecruitCompanyProfileDO::getStatus, profile.getStatus());
        companyProfileMapper.update(profile, update);
        return current.getId();
    }
}
