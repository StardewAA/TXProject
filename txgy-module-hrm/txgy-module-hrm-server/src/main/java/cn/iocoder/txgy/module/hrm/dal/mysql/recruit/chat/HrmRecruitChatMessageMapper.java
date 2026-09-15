package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.chat;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat.HrmRecruitChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collections;
import java.util.List;

@Mapper
public interface HrmRecruitChatMessageMapper extends BaseMapperX<HrmRecruitChatMessageDO> {

    default HrmRecruitChatMessageDO selectLatestResumeByConversationId(Long conversationId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitChatMessageDO>()
                .eq(HrmRecruitChatMessageDO::getConversationId, conversationId)
                .eq(HrmRecruitChatMessageDO::getContentType, 2)
                .orderByDesc(HrmRecruitChatMessageDO::getId)
                .last("LIMIT 1"));
    }

    default List<HrmRecruitChatMessageDO> selectListByConversation(Long conversationId, Long minId, Integer limit) {
        int size = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        if (minId != null) {
            return selectList(new LambdaQueryWrapperX<HrmRecruitChatMessageDO>()
                    .eq(HrmRecruitChatMessageDO::getConversationId, conversationId)
                    .gt(HrmRecruitChatMessageDO::getId, minId)
                    .orderByAsc(HrmRecruitChatMessageDO::getId)
                    .last("LIMIT " + size));
        }
        List<HrmRecruitChatMessageDO> list = selectList(new LambdaQueryWrapperX<HrmRecruitChatMessageDO>()
                .eq(HrmRecruitChatMessageDO::getConversationId, conversationId)
                .orderByDesc(HrmRecruitChatMessageDO::getId)
                .last("LIMIT " + size));
        Collections.reverse(list);
        return list;
    }
}
