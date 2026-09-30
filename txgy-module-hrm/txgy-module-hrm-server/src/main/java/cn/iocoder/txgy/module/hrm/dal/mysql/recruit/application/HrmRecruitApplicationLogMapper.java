package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmRecruitApplicationLogMapper extends BaseMapperX<HrmRecruitApplicationLogDO> {

    /**
     * 按应聘编号批量查询状态流转日志（招聘漏斗按阶段统计用）
     */
    default List<HrmRecruitApplicationLogDO> selectListByApplicationIds(Collection<Long> applicationIds) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitApplicationLogDO>()
                .in(HrmRecruitApplicationLogDO::getApplicationId, applicationIds));
    }
}
