package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentModelDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 云端模型配置 Mapper（全局一套，租户内可多行但只用启用的第一条） */
@Mapper
public interface BidAgentModelMapper extends BaseMapperX<BidAgentModelDO> {

    default List<BidAgentModelDO> selectListOrdered() {
        return selectList(new LambdaQueryWrapperX<BidAgentModelDO>().orderByAsc(BidAgentModelDO::getId));
    }

    /**
     * 物理清理该名称下已逻辑删除的历史行。
     * 唯一键 uk_tenant_name 含 deleted 后，同名只能存在一条已删行；新增（含复制平台行为租户行）前清理，避免二次删除撞唯一键。
     */
    @Delete("DELETE FROM bidding_bid_agent_model WHERE name = #{name} AND deleted = 1")
    int purgeDeletedByName(String name);

    /** 取启用的模型配置（多条时取最早建的），没有则返回 null */
    default BidAgentModelDO selectEnabledOne() {
        return selectOne(new LambdaQueryWrapperX<BidAgentModelDO>()
                .eq(BidAgentModelDO::getEnabled, 1)
                .orderByAsc(BidAgentModelDO::getId)
                .last("LIMIT 1"));
    }
}
