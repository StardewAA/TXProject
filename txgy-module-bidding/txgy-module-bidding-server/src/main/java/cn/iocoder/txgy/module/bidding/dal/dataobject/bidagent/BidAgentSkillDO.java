package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 技能 DO：轻量"提示词增强包"（片段注入 system prompt + 建议工具/知识库角色），可自动挂载到适用智能体
 */
@TableName("bidding_bid_agent_skill")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BidAgentSkillDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 技能编码（租户内唯一） */
    private String code;

    private String name;

    private String description;

    /** 提示词片段（注入 system prompt） */
    private String promptFragment;

    /** 建议挂载的知识库角色：TENDER/FACT/TEMPLATE */
    private String knowledgeRoles;

    /** 建议/限定的工具白名单（逗号分隔） */
    private String toolWhitelist;

    /** 适用任务类型：PARSE/OUTLINE/DRAFT/FILL */
    private String applicableTaskTypes;

    /** 是否自动挂载：1 是 0 否 */
    private Integer autoAttach;

    /** 状态：0 启用 1 停用 */
    private Integer status;

    private Integer sort;

    private String remark;
}
