package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMcpServerDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/** MCP 服务 Mapper */
@Mapper
public interface BidAgentMcpServerMapper extends BaseMapperX<BidAgentMcpServerDO> {

    default List<BidAgentMcpServerDO> selectListOrdered() {
        return selectList(new LambdaQueryWrapperX<BidAgentMcpServerDO>().orderByAsc(BidAgentMcpServerDO::getId));
    }

    default BidAgentMcpServerDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapperX<BidAgentMcpServerDO>().eq(BidAgentMcpServerDO::getName, name));
    }

    /**
     * 物理清理该名称下已逻辑删除的历史行。
     * 唯一键 uk_tenant_name 含 deleted 后，同名只能存在一条已删行；重建前清理，避免「删除→重建→再删除」时撞唯一键。
     */
    @Delete("DELETE FROM bidding_bid_agent_mcp_server WHERE name = #{name} AND deleted = 1")
    int purgeDeletedByName(String name);

    default List<BidAgentMcpServerDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<BidAgentMcpServerDO>().in(BidAgentMcpServerDO::getId, ids));
    }
}
