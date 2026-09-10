package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.resume;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmRecruitResumeMapper extends BaseMapperX<HrmRecruitResumeDO> {

    default List<HrmRecruitResumeDO> selectListByCandidateId(Long candidateId) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitResumeDO>()
                .eq(HrmRecruitResumeDO::getCandidateId, candidateId)
                .orderByDesc(HrmRecruitResumeDO::getVersionNo)
                .orderByDesc(HrmRecruitResumeDO::getId));
    }

    default HrmRecruitResumeDO selectByIdAndCandidateId(Long id, Long candidateId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitResumeDO>()
                .eq(HrmRecruitResumeDO::getId, id)
                .eq(HrmRecruitResumeDO::getCandidateId, candidateId));
    }

    default int markReplacedByCandidateId(Long candidateId) {
        return update(new LambdaUpdateWrapper<HrmRecruitResumeDO>()
                .eq(HrmRecruitResumeDO::getCandidateId, candidateId)
                .eq(HrmRecruitResumeDO::getStatus, 0)
                .set(HrmRecruitResumeDO::getStatus, 1));
    }
}
