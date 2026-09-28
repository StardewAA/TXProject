package cn.iocoder.txgy.module.bidding.service.flow;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.GovernmentApprovalReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.GovernmentApprovalRespVO;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.flow.GovernmentApprovalActionEnum;
import cn.iocoder.txgy.module.bidding.enums.flow.GovernmentApprovalBizTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 政府审批服务实现（预留 stub）。
 *
 * <p>现政府审批接口尚未建设：本实现直接按入参动作返回审批结论（APPROVE=通过 / REJECT=驳回），
 * 并生成模拟审批文号。接入真实审批系统后在本类替换网关调用即可，调用方契约不变。
 */
@Service
public class GovernmentApprovalServiceImpl implements GovernmentApprovalService {

    @Override
    public GovernmentApprovalRespVO approval(GovernmentApprovalReqVO reqVO) {
        // 1. 参数校验（预留接口同样校验，避免脏调用污染后续真实对接）
        GovernmentApprovalBizTypeEnum bizType = GovernmentApprovalBizTypeEnum.of(reqVO.getBizType());
        if (bizType == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.GOV_APPROVAL_BIZ_TYPE_INVALID, reqVO.getBizType());
        }
        if (!StringUtils.hasText(reqVO.getBizId())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.GOV_APPROVAL_BIZ_ID_REQUIRED);
        }
        GovernmentApprovalActionEnum action = GovernmentApprovalActionEnum.of(reqVO.getAction());
        if (action == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.GOV_APPROVAL_ACTION_INVALID, reqVO.getAction());
        }
        // 2. 预留 stub：直接返回审批结论（真实实现：调用政府审批/监管平台并回推审批文号）
        boolean approved = action == GovernmentApprovalActionEnum.APPROVE;
        GovernmentApprovalRespVO resp = new GovernmentApprovalRespVO();
        resp.setApproved(approved);
        resp.setApprovalNo("GOV-STUB-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + reqVO.getBizType() + "-" + reqVO.getBizId());
        resp.setApprovedTime(LocalDateTime.now());
        resp.setMessage(approved
                ? "预留接口 stub：审批通过（" + bizType.getLabel() + " " + reqVO.getBizId() + "）"
                : "预留接口 stub：审批驳回（" + bizType.getLabel() + " " + reqVO.getBizId() + "）");
        resp.setStub(Boolean.TRUE);
        return resp;
    }
}
