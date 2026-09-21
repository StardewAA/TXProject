package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.demand;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand.HrmRecruitDemandPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.demand.HrmRecruitDemandDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmRecruitDemandMapper extends BaseMapperX<HrmRecruitDemandDO> {

    default PageResult<HrmRecruitDemandDO> selectPage(HrmRecruitDemandPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmRecruitDemandDO>()
                .likeIfPresent(HrmRecruitDemandDO::getDemandNo, reqVO.getDemandNo())
                .likeIfPresent(HrmRecruitDemandDO::getDemandName, reqVO.getDemandName())
                .likeIfPresent(HrmRecruitDemandDO::getWorkType, reqVO.getWorkType())
                .eqIfPresent(HrmRecruitDemandDO::getDeptId, reqVO.getDeptId())
                .eqIfPresent(HrmRecruitDemandDO::getStatus, reqVO.getStatus())
                .eqIfPresent(HrmRecruitDemandDO::getUrgency, reqVO.getUrgency())
                .betweenIfPresent(HrmRecruitDemandDO::getPlanEntryDate, reqVO.getPlanEntryDate())
                .orderByDesc(HrmRecruitDemandDO::getId));
    }

    default List<HrmRecruitDemandDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitDemandDO>()
                .eqIfPresent(HrmRecruitDemandDO::getStatus, status)
                .orderByDesc(HrmRecruitDemandDO::getId));
    }

    default HrmRecruitDemandDO selectByDemandNo(String demandNo) {
        return selectOne(HrmRecruitDemandDO::getDemandNo, demandNo);
    }

}
