package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.chat;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat.HrmRecruitChatConversationDO;
import cn.iocoder.txgy.module.hrm.enums.recruit.chat.HrmRecruitChatStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmRecruitChatConversationMapper extends BaseMapperX<HrmRecruitChatConversationDO> {

    default HrmRecruitChatConversationDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(HrmRecruitChatConversationDO::getId, id);
    }

    default HrmRecruitChatConversationDO selectByPostAndUsers(Long postId, Long candidateUserId, Long hrUserId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitChatConversationDO>()
                .eq(HrmRecruitChatConversationDO::getPostId, postId)
                .eq(HrmRecruitChatConversationDO::getCandidateUserId, candidateUserId)
                .eq(HrmRecruitChatConversationDO::getHrUserId, hrUserId)
                .ne(HrmRecruitChatConversationDO::getStatus, HrmRecruitChatStatusEnum.FAILED.getStatus()));
    }

    default HrmRecruitChatConversationDO selectActiveByPostAndCandidateUser(Long postId, Long candidateUserId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitChatConversationDO>()
                .eq(HrmRecruitChatConversationDO::getPostId, postId)
                .eq(HrmRecruitChatConversationDO::getCandidateUserId, candidateUserId)
                .ne(HrmRecruitChatConversationDO::getStatus, HrmRecruitChatStatusEnum.FAILED.getStatus())
                .orderByDesc(HrmRecruitChatConversationDO::getId)
                .last("LIMIT 1"));
    }

    default List<HrmRecruitChatConversationDO> selectWaitingBefore(java.time.LocalDateTime deadline) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitChatConversationDO>()
                .eq(HrmRecruitChatConversationDO::getStatus, HrmRecruitChatStatusEnum.WAITING.getStatus())
                .isNull(HrmRecruitChatConversationDO::getHrReplyTime)
                .lt(HrmRecruitChatConversationDO::getLastTime, deadline)
                .orderByAsc(HrmRecruitChatConversationDO::getId));
    }

    default int markWaitingAsFailed(Long id, java.time.LocalDateTime endTime, String reason) {
        HrmRecruitChatConversationDO update = new HrmRecruitChatConversationDO()
                .setId(id)
                .setStatus(HrmRecruitChatStatusEnum.FAILED.getStatus())
                .setEndTime(endTime)
                .setEndReason(reason);
        return update(update, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<HrmRecruitChatConversationDO>()
                .eq(HrmRecruitChatConversationDO::getId, id)
                .eq(HrmRecruitChatConversationDO::getStatus, HrmRecruitChatStatusEnum.WAITING.getStatus())
                .isNull(HrmRecruitChatConversationDO::getHrReplyTime));
    }

    default List<HrmRecruitChatConversationDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitChatConversationDO>()
                .and(wrapper -> wrapper.eq(HrmRecruitChatConversationDO::getCandidateUserId, userId)
                        .or()
                        .eq(HrmRecruitChatConversationDO::getHrUserId, userId))
                .orderByDesc(HrmRecruitChatConversationDO::getLastTime)
                .orderByDesc(HrmRecruitChatConversationDO::getId));
    }
}
