package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitOfferApprovalStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmRecruitOfferMapper extends BaseMapperX<HrmRecruitOfferDO> {
    default HrmRecruitOfferDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(HrmRecruitOfferDO::getId, id);
    }

    default HrmRecruitOfferDO selectByApplicationId(Long applicationId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitOfferDO>()
                .eq(HrmRecruitOfferDO::getApplicationId, applicationId));
    }

    default List<HrmRecruitOfferDO> selectListByApplicationIds(Collection<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<HrmRecruitOfferDO>()
                .in(HrmRecruitOfferDO::getApplicationId, applicationIds));
    }

    default List<HrmRecruitOfferDO> selectSentBefore(LocalDate date) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitOfferDO>()
                .eq(HrmRecruitOfferDO::getApprovalStatus, HrmRecruitOfferApprovalStatusEnum.SENT.getStatus())
                .isNotNull(HrmRecruitOfferDO::getValidUntil)
                .lt(HrmRecruitOfferDO::getValidUntil, date));
    }

    default int updateApprovalWithVersion(Long id, Integer version, HrmRecruitOfferDO updateObj) {
        return update(updateObj, new LambdaUpdateWrapper<HrmRecruitOfferDO>()
                .eq(HrmRecruitOfferDO::getId, id).eq(HrmRecruitOfferDO::getApprovalVersion, version));
    }
}
