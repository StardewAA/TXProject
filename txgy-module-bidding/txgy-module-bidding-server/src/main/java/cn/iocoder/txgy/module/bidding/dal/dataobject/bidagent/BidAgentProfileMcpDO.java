package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 智能体 ↔ MCP 服务关联 DO */
@TableName("bidding_bid_agent_profile_mcp")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BidAgentProfileMcpDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long profileId;

    private Long mcpServerId;

    /** 允许的工具子集（JSON 数组；空=全部） */
    private String toolNames;
}
