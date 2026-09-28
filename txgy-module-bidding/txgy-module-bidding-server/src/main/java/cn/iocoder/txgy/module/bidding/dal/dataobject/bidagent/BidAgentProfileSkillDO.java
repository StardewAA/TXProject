package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 智能体 ↔ 技能关联 DO */
@TableName("bidding_bid_agent_profile_skill")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BidAgentProfileSkillDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 智能体配置编号（bidding_bid_agent.id） */
    private Long profileId;

    /** 技能编号 */
    private Long skillId;
}
