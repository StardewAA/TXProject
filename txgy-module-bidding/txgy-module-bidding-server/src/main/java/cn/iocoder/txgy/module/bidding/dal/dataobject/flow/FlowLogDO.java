package cn.iocoder.txgy.module.bidding.dal.dataobject.flow;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 招标全流程状态流转留痕（G 轨：业务编号全局定位，审计只追加）。表：bidding_flow_log。
 *
 * <p>规范 §六-2：每次状态变更必须记录操作人、操作时间、变更前状态、变更后状态、操作 IP。
 * 本表承接该留痕；流转动作由 FlowTransitionService 统一执行（禁止业务代码直接 setStatus）。
 */
@TableName("bidding_flow_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowLogDO extends BaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 实体类型：PROJECT/TENDER_PROJECT/BID_SECTION */
    private String bizType;
    /** 业务编号（project_code/tender_project_code/bid_section_code） */
    private String bizCode;
    /** 流转前状态（状态枚举 code；首次流转可为空） */
    private String fromState;
    /** 流转后状态（状态枚举 code） */
    private String toState;
    /** 流转事件（FlowTransitionEventEnum code） */
    private String event;
    /** 操作人 */
    private String operator;
    /** 操作 IP */
    private String operatorIp;
    /** 流转说明/审批意见 */
    private String remark;
}
