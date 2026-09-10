package cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmPersonalResumeMapper extends BaseMapperX<HrmPersonalResumeDO> {

    default List<HrmPersonalResumeDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<HrmPersonalResumeDO>()
                .eq(HrmPersonalResumeDO::getUserId, userId)
                .orderByDesc(HrmPersonalResumeDO::getCreateTime)
                .orderByDesc(HrmPersonalResumeDO::getId));
    }

    default HrmPersonalResumeDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<HrmPersonalResumeDO>()
                .eq(HrmPersonalResumeDO::getId, id)
                .eq(HrmPersonalResumeDO::getUserId, userId));
    }
}
