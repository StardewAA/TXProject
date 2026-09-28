package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.Collection;
import java.util.List;

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
        return selectOneForUpdate(new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getCandidateId, candidateId)
                .eq(HrmRecruitApplicationDO::getPostId, postId)
                .in(HrmRecruitApplicationDO::getStatus, statuses));
    }

    default HrmRecruitApplicationDO selectProcessingByApplicantAndPost(Long applicantUserId, Long postId,
                                                                          Collection<String> statuses) {
        return selectOneForUpdate(new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getApplicantUserId, applicantUserId)
                .eq(HrmRecruitApplicationDO::getPostId, postId)
                .in(HrmRecruitApplicationDO::getStatus, statuses));
    }

    default List<HrmRecruitApplicationDO> selectListByApplicantUserId(Long applicantUserId) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getApplicantUserId, applicantUserId)
                .orderByDesc(HrmRecruitApplicationDO::getCreateTime)
                .orderByDesc(HrmRecruitApplicationDO::getId));
    }

    default HrmRecruitApplicationDO selectLatestByCandidateAndPost(Long candidateId, Long postId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getCandidateId, candidateId)
                .eq(HrmRecruitApplicationDO::getPostId, postId)
                .orderByDesc(HrmRecruitApplicationDO::getCreateTime)
                .orderByDesc(HrmRecruitApplicationDO::getId)
                .last("LIMIT 1"));
    }

    default List<HrmRecruitApplicationDO> selectListByCandidateIds(Collection<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .in(HrmRecruitApplicationDO::getCandidateId, candidateIds)
                .orderByAsc(HrmRecruitApplicationDO::getCandidateId)
                .orderByDesc(HrmRecruitApplicationDO::getStatusUpdateTime)
                .orderByDesc(HrmRecruitApplicationDO::getId));
    }

    default HrmRecruitApplicationDO selectCurrentByCandidateId(Long candidateId) {
        List<HrmRecruitApplicationDO> applications = selectListByCandidateIds(List.of(candidateId));
        return applications.stream()
                .filter(application -> HrmRecruitApplicationStatusEnum.PROCESSING_STATUSES
                        .contains(application.getStatus()))
                .findFirst()
                .orElse(applications.stream().findFirst().orElse(null));
    }

    /** 查询候选人当前各岗位的应聘记录，结果按最新投递时间倒序。 */
    default List<HrmRecruitApplicationDO> selectListByCandidateIdsAndPostIds(
            Collection<Long> candidateIds, Collection<Long> postIds) {
        if (candidateIds == null || candidateIds.isEmpty() || postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .in(HrmRecruitApplicationDO::getCandidateId, candidateIds)
                .in(HrmRecruitApplicationDO::getPostId, postIds)
                .orderByDesc(HrmRecruitApplicationDO::getCreateTime)
                .orderByDesc(HrmRecruitApplicationDO::getId));
    }

    default PageResult<HrmRecruitApplicationDO> selectPageByApplicantUserId(
            Long applicantUserId, HrmRecruitApplicationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getApplicantUserId, applicantUserId)
                .eqIfPresent(HrmRecruitApplicationDO::getStatus, reqVO.getStatus())
                .orderByDesc(HrmRecruitApplicationDO::getCreateTime));
    }

    default HrmRecruitApplicationDO selectByIdAndApplicantUserId(Long id, Long applicantUserId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getId, id)
                .eq(HrmRecruitApplicationDO::getApplicantUserId, applicantUserId));
    }

    default int updateStatusWithVersion(Long id, Integer version, HrmRecruitApplicationDO updateObj) {
        return update(updateObj, new LambdaUpdateWrapper<HrmRecruitApplicationDO>()
                .eq(HrmRecruitApplicationDO::getId, id)
                .eq(HrmRecruitApplicationDO::getVersion, version));
    }
}
