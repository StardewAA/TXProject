package cn.iocoder.txgy.module.hrm.service.recruit.company;

import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company.HrmRecruitCompanyProfileVO;
import cn.iocoder.txgy.module.hrm.enums.config.HrmConfigTypeEnum;
import cn.iocoder.txgy.module.hrm.service.config.HrmConfigService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class HrmRecruitCompanyProfileService {
    @Resource
    private HrmConfigService configService;

    public HrmRecruitCompanyProfileVO getProfile() {
        var values = configService.getConfigValueList(HrmConfigTypeEnum.RECRUIT_COMPANY_PROFILE.getType());
        if (values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return new HrmRecruitCompanyProfileVO();
        }
        return JSONUtil.toBean(values.get(0), HrmRecruitCompanyProfileVO.class);
    }

    public void saveProfile(HrmRecruitCompanyProfileVO profile) {
        configService.replaceConfigValueList(
                HrmConfigTypeEnum.RECRUIT_COMPANY_PROFILE.getType(),
                java.util.List.of(JSONUtil.toJsonStr(profile)));
    }
}
