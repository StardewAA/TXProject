package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentProfileSkillDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 智能体↔技能关联 Mapper */
@Mapper
public interface BidAgentProfileSkillMapper extends BaseMapperX<BidAgentProfileSkillDO> {

    default List<BidAgentProfileSkillDO> selectListByProfileId(Long profileId) {
        return selectList(new LambdaQueryWrapperX<BidAgentProfileSkillDO>()
                .eq(BidAgentProfileSkillDO::getProfileId, profileId));
    }

    /**
     * 按智能体清空关联（物理删除）。
     * 关联表无审计价值，逻辑删除行会与唯一键 uk_profile_skill 互斥，导致「取消勾选后再勾选」报唯一键冲突。
     */
    @Delete("DELETE FROM bidding_bid_agent_profile_skill WHERE profile_id = #{profileId}")
    int deleteByProfileId(Long profileId);
}
