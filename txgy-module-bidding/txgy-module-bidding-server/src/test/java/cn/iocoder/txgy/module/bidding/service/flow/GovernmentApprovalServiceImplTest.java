package cn.iocoder.txgy.module.bidding.service.flow;

import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.GovernmentApprovalReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.GovernmentApprovalRespVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 政府审批预留接口 stub 行为锁定（当前直接返回通过；接入真实审批系统后更新本测试）。
 */
class GovernmentApprovalServiceImplTest {

    private final GovernmentApprovalServiceImpl service = new GovernmentApprovalServiceImpl();

    private GovernmentApprovalReqVO req(String bizType, String bizId, String action) {
        GovernmentApprovalReqVO req = new GovernmentApprovalReqVO();
        req.setBizType(bizType);
        req.setBizId(bizId);
        req.setAction(action);
        return req;
    }

    @Test
    void approval_shouldReturnPassForApprove() {
        GovernmentApprovalRespVO resp = service.approval(req("PROJECT", "P-1", "APPROVE"));
        assertTrue(resp.getApproved());
        assertTrue(resp.getStub());
        assertNotNull(resp.getApprovedTime());
        assertTrue(resp.getApprovalNo().startsWith("GOV-STUB-"));
        assertNotNull(resp.getMessage());
    }

    @Test
    void approval_shouldReturnRejectForReject() {
        GovernmentApprovalRespVO resp = service.approval(req("TENDER_PROJECT", "T-1", "REJECT"));
        assertFalse(resp.getApproved());
        assertTrue(resp.getStub());
    }

    @Test
    void approval_shouldRejectInvalidBizType() {
        assertThrows(Exception.class, () -> service.approval(req("XXX", "1", "APPROVE")));
    }

    @Test
    void approval_shouldRequireBizId() {
        assertThrows(Exception.class, () -> service.approval(req("PROJECT", " ", "APPROVE")));
    }

    @Test
    void approval_shouldRejectInvalidAction() {
        assertThrows(Exception.class, () -> service.approval(req("PROJECT", "1", "MAYBE")));
    }
}
