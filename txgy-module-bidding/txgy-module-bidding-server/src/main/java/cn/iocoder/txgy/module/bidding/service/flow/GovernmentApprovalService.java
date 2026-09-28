package cn.iocoder.txgy.module.bidding.service.flow;

import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.GovernmentApprovalReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.GovernmentApprovalRespVO;

/**
 * 政府审批服务（预留接口）。
 *
 * <p>对外预留的政府/监管审批网关契约（详见 docs/前端规范-全域接口说明/27-政府审批接口预留-2026-09-20.md）。
 * 当前实现 {@link GovernmentApprovalServiceImpl} 为 stub：直接返回审批结论（默认通过），
 * 接入真实政府审批系统后替换实现（同步/异步回推均可，契约不变）。
 */
public interface GovernmentApprovalService {

    /**
     * 政府审批回调：对指定审批对象给出审批结论。
     *
     * @param reqVO 审批请求（bizType + bizId + action APPROVE/REJECT）
     * @return 审批结论（当前 stub 直接返回通过，stub=true 标识）
     */
    GovernmentApprovalRespVO approval(GovernmentApprovalReqVO reqVO);
}
