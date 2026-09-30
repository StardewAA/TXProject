package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

// [规范依据]《招投标模块开发规范》§4.2
// [业务目标] 智能体配置：后台可调提示词/模型/温度/挂载知识库角色，前台编排按 task_type 读取生效配置
// [为什么] 后台"优化前台体验"必须有一个可改、可回滚的配置载体；
//          提示词统一以后台为唯一事实源（2026-09-28 起五类智能体均后台可编辑），底座按 task_type
//          拉取运行配置即生效，内置提示词仅作拉取失败兜底；editable=0 作为锁机制保留（保存仍会拒绝）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("bidding_bid_agent")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidAgentProfileDO extends TenantBaseDO {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 任务类型：PARSE/OUTLINE/DRAFT/FILL/REVIEW */
    private String taskType;
    /** 智能体名称 */
    private String name;
    /** 一句话说明 */
    private String description;
    /** 系统提示词 */
    private String systemPrompt;
    /** 模型名（空=用平台默认） */
    private String model;
    /** 温度（空=用平台默认） */
    private BigDecimal temperature;
    /** 单条回复最大 token（空=用全局模型配置） */
    private Integer maxTokens;
    /** 默认挂载的知识库角色，逗号分隔 */
    private String knowledgeRoles;
    /** 内置工具白名单（逗号分隔，空=全部允许） */
    private String toolWhitelist;
    /** 是否后台可编辑：1 可编辑 0 只读 */
    private Integer editable;
    /** 是否自动挂载推荐技能（1 是 0 否） */
    private Integer skillAutoAttach;
    /** 排序 */
    private Integer sort;
    /** 状态：0 开启 1 停用 */
    private Integer status;
    /** 备注 */
    private String remark;
}
