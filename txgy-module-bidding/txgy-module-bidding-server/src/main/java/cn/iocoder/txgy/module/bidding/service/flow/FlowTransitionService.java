package cn.iocoder.txgy.module.bidding.service.flow;

import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowLogRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowNextActionRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTransitionReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTransitionRespVO;

import java.util.List;

/**
 * 招标全流程状态流转服务（事件驱动，禁止业务代码直接 setStatus）。
 *
 * <p>规范 §六-1：状态变更必须通过动作方法——内部：状态机校验（事件映射 + canTransit 双保险）→ 状态变更 → 留痕。
 * 测试模式（bidding.flow.mode=TEST）下由招标方/平台方/政府审批方点击「下一步/审批通过」触发；
 * PROD 模式由定时任务（时间驱动）与真实审批接口（替换 GovernmentApprovalServiceImpl）调用。
 */
public interface FlowTransitionService {

    /**
     * 执行一次状态流转（手动入口：受 manual-advance-enabled 开关约束）。
     *
     * @param reqVO bizType + bizCode + event（FlowTransitionEventEnum code）
     * @return 流转结果（from → to）
     */
    FlowTransitionRespVO transition(FlowTransitionReqVO reqVO);

    /**
     * 执行一次状态流转（时间驱动入口：由 TimeEventScheduler 调用，不受 manual-advance-enabled 开关约束，
     * 但同样做状态机校验与留痕；状态不匹配抛 FLOW_EVENT_INVALID，由调度器捕获标记 SKIPPED）。
     */
    FlowTransitionRespVO transitionByTimer(FlowTransitionReqVO reqVO);

    /**
     * 查询实体当前状态（调度器判定时间事件是否已过时用）；实体不存在/类型不支持返回 null。
     */
    String getCurrentState(String bizType, String bizCode);

    /** 查询当前状态下可执行的动作列表（前端「下一步」按钮数据源）。 */
    List<FlowNextActionRespVO> nextActions(String bizType, String bizCode);

    /** 查询流转留痕（按实体，时间倒序）。 */
    List<FlowLogRespVO> logs(String bizType, String bizCode);
}
