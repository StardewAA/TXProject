package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface HrmRecruitOfferMapper extends BaseMapperX<HrmRecruitOfferDO> {
    default HrmRecruitOfferDO selectByApplicationId(Long applicationId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitOfferDO>()
                .eq(HrmRecruitOfferDO::getApplicationId, applicationId));
    }

    default int updateApprovalWithVersion(Long id, Integer version, HrmRecruitOfferDO updateObj) {
        return update(updateObj, new LambdaUpdateWrapper<HrmRecruitOfferDO>()
                .eq(HrmRecruitOfferDO::getId, id).eq(HrmRecruitOfferDO::getApprovalVersion, version));
    }
}
