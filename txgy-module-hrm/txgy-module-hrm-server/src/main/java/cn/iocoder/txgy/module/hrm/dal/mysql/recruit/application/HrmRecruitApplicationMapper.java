package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.Collection;

@Mapper
public interface HrmRecruitApplicationMapper extends BaseMapperX<HrmRecruitApplicationDO> {

    default PageResult<HrmRecruitApplicationDO> selectPage(HrmRecruitApplicationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .eqIfPresent(HrmRecruitApplicationDO::getCandidateId, reqVO.getCandidateId())
                .eqIfPresent(HrmRecruitApplicationDO::getPostId, reqVO.getPostId())
                .eqIfPresent(HrmRecruitApplicationDO::getChannelId, reqVO.getChannelId())
                .eqIfPresent(HrmRecruitApplicationDO::getStatus, reqVO.getStatus())
                .orderByDesc(HrmRecruitApplicationDO::getCreateTime));
    }

    default HrmRecruitApplicationDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(HrmRecruitApplicationDO::getId, id);
    }

    default HrmRecruitApplicationDO selectProcessingByCandidateAndPost(Long candidateId, Long postId,
                                                                         Collection<String> statuses) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getCandidateId, candidateId)
                .eq(HrmRecruitApplicationDO::getPostId, postId)
                .in(HrmRecruitApplicationDO::getStatus, statuses));
    }

    default int updateStatusWithVersion(Long id, Integer version, HrmRecruitApplicationDO updateObj) {
        return update(updateObj, new LambdaUpdateWrapper<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getId, id)
                .eq(HrmRecruitApplicationDO::getVersion, version));
    }
}
