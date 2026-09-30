package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentSkillDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/** 技能 Mapper */
@Mapper
public interface BidAgentSkillMapper extends BaseMapperX<BidAgentSkillDO> {

    default List<BidAgentSkillDO> selectListOrdered() {
        return selectList(new LambdaQueryWrapperX<BidAgentSkillDO>()
                .orderByAsc(BidAgentSkillDO::getSort)
                .orderByAsc(BidAgentSkillDO::getId));
    }

    default BidAgentSkillDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapperX<BidAgentSkillDO>().eq(BidAgentSkillDO::getCode, code));
    }

    /**
     * 物理清理该编码下已逻辑删除的历史行。
     * 唯一键 uk_tenant_code 含 deleted 后，同编码只能存在一条已删行；重建前清理，避免「删除→重建→再删除」时撞唯一键。
     */
    @Delete("DELETE FROM bidding_bid_agent_skill WHERE code = #{code} AND deleted = 1")
    int purgeDeletedByCode(String code);

    /** 启用的、可自动挂载的技能（用于智能体技能自动挂载） */
    default List<BidAgentSkillDO> selectEnabledAutoAttachList() {
        return selectList(new LambdaQueryWrapperX<BidAgentSkillDO>()
                .eq(BidAgentSkillDO::getStatus, 0)
                .eq(BidAgentSkillDO::getAutoAttach, 1)
                .orderByAsc(BidAgentSkillDO::getSort));
    }

    default List<BidAgentSkillDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<BidAgentSkillDO>().in(BidAgentSkillDO::getId, ids));
    }
}
