package cn.iocoder.txgy.module.bidding.service.flow;

import cn.iocoder.txgy.module.bidding.enums.flow.FlowTransitionEventEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 状态流转事件映射锁定（事件 → 目标状态；与各状态枚举 canTransit 双保险，规则见契约 26）。
 *
 * <p>为什么只测映射：映射是流转服务的入口规则，一旦错（如评审完直接定标、归档后还能终止），
 * 测试模式下「下一步」按钮会给出非法动作；映射在服务层集中维护并双保险校验。
 */
class FlowTransitionRuleTest {

    // ==================== 项目 ====================

    @Test
    void project_shouldMapMainChain() {
        assertEquals("APPROVING", FlowTransitionServiceImpl.resolveTarget("PROJECT", "DRAFT", "SUBMIT_APPROVAL"));
        assertEquals("ACTIVE", FlowTransitionServiceImpl.resolveTarget("PROJECT", "APPROVING", "APPROVE_PASS"));
        assertEquals("DRAFT", FlowTransitionServiceImpl.resolveTarget("PROJECT", "APPROVING", "APPROVE_REJECT"));
        assertEquals("COMPLETED", FlowTransitionServiceImpl.resolveTarget("PROJECT", "ACTIVE", "CONTRACT_SIGNED"));
        assertEquals("CANCELLED", FlowTransitionServiceImpl.resolveTarget("PROJECT", "DRAFT", "CANCEL"));
        assertEquals("CANCELLED", FlowTransitionServiceImpl.resolveTarget("PROJECT", "ACTIVE", "CANCEL"));
        assertNull(FlowTransitionServiceImpl.resolveTarget("PROJECT", "COMPLETED", "CANCEL")); // 终态
        assertNull(FlowTransitionServiceImpl.resolveTarget("PROJECT", "DRAFT", "APPROVE_PASS")); // 未审批先通过
    }

    // ==================== 招标项目 ====================

    @Test
    void tender_shouldMapMainChain() {
        assertEquals("APPROVING", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "DRAFT", "SUBMIT_APPROVAL"));
        assertEquals("PREPARING", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "APPROVING", "APPROVE_PASS"));
        assertEquals("ANNOUNCED", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "PREPARING", "PUBLISH_TENDER_NOTICE"));
        assertEquals("BIDDING", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "ANNOUNCED", "BIDDING_STARTED"));
        assertEquals("PENDING_OPEN", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "BIDDING", "BIDDING_ENDED"));
        assertEquals("EVALUATING", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "PENDING_OPEN", "BID_OPENED"));
        assertEquals("PUBLICIZING", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "EVALUATING", "EVALUATION_FINISHED"));
        assertEquals("AWARDED", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "PUBLICIZING", "PUBLISH_RESULT_NOTICE"));
        assertEquals("CONTRACTING", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "AWARDED", "ISSUE_AWARD_NOTICE"));
        assertEquals("COMPLETED", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "CONTRACTING", "CONTRACT_SIGNED"));
    }

    @Test
    void tender_shouldMapReconsiderAndTerminate() {
        assertEquals("DRAFT", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "APPROVING", "APPROVE_REJECT"));
        assertEquals("EVALUATING", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "PUBLICIZING", "OBJECTION_RECONSIDER")); // 异议复核
        assertEquals("TERMINATED", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "PREPARING", "TERMINATE"));
        assertEquals("TERMINATED", FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "BIDDING", "TERMINATE"));
        assertNull(FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "COMPLETED", "TERMINATE")); // 归档不可终止
        assertNull(FlowTransitionServiceImpl.resolveTarget("TENDER_PROJECT", "ANNOUNCED", "EVALUATION_FINISHED")); // 跳阶段
    }

    // ==================== 标段 ====================

    @Test
    void section_shouldMapMainChainAndFail() {
        assertEquals("BIDDING", FlowTransitionServiceImpl.resolveTarget("BID_SECTION", "DRAFT", "PUBLISH_TENDER_NOTICE"));
        assertEquals("EVALUATING", FlowTransitionServiceImpl.resolveTarget("BID_SECTION", "BIDDING", "BID_OPENED"));
        assertEquals("AWARDED", FlowTransitionServiceImpl.resolveTarget("BID_SECTION", "EVALUATING", "PUBLISH_RESULT_NOTICE"));
        assertEquals("COMPLETED", FlowTransitionServiceImpl.resolveTarget("BID_SECTION", "AWARDED", "CONTRACT_SIGNED"));
        assertEquals("FAILED", FlowTransitionServiceImpl.resolveTarget("BID_SECTION", "BIDDING", "TERMINATE")); // 不足3家流标
        assertEquals("FAILED", FlowTransitionServiceImpl.resolveTarget("BID_SECTION", "EVALUATING", "TERMINATE")); // 全废标
        assertNull(FlowTransitionServiceImpl.resolveTarget("BID_SECTION", "FAILED", "BID_OPENED")); // 终态
        assertNull(FlowTransitionServiceImpl.resolveTarget("BID_SECTION", "DRAFT", "BID_OPENED")); // 跳阶段
    }

    // ==================== 通用 ====================

    @Test
    void events_shouldMarkTimeDrivenOnly() {
        // 时间轴事件（投标开始/截止、公示过期）：手动执行受 manual-advance-enabled 控制
        assertEquals(true, FlowTransitionEventEnum.BIDDING_STARTED.isTimeDriven());
        assertEquals(true, FlowTransitionEventEnum.BIDDING_ENDED.isTimeDriven());
        assertEquals(true, FlowTransitionEventEnum.EXPIRE.isTimeDriven());
        // 操作/审批事件：始终允许手动
        assertEquals(false, FlowTransitionEventEnum.SUBMIT_APPROVAL.isTimeDriven());
        assertEquals(false, FlowTransitionEventEnum.APPROVE_PASS.isTimeDriven());
        assertEquals(false, FlowTransitionEventEnum.PUBLISH_TENDER_NOTICE.isTimeDriven());
        assertEquals(false, FlowTransitionEventEnum.BID_OPENED.isTimeDriven());
        assertEquals(false, FlowTransitionEventEnum.TERMINATE.isTimeDriven());
        assertEquals(false, FlowTransitionEventEnum.CANCEL.isTimeDriven());
    }

    @Test
    void linkage_shouldJudgeProjectCompletion() {
        // 项目完结联动判定：全部招标项目终态（COMPLETED/TERMINATED）才完结
        assertEquals(false, FlowTransitionServiceImpl.allTenderProjectsTerminal(java.util.List.of())); // 无下属招标项目不自动完结
        assertEquals(false, FlowTransitionServiceImpl.allTenderProjectsTerminal(java.util.List.of("BIDDING", "COMPLETED")));
        assertEquals(true, FlowTransitionServiceImpl.allTenderProjectsTerminal(java.util.List.of("COMPLETED")));
        assertEquals(true, FlowTransitionServiceImpl.allTenderProjectsTerminal(java.util.List.of("COMPLETED", "TERMINATED")));
        assertEquals(false, FlowTransitionServiceImpl.allTenderProjectsTerminal(null));
    }

    @Test
    void timer_shouldJudgeEventPassed() {
        // 时间事件过时判定（调度器 SKIP/等待）：BIDDING_STARTED 前置 ANNOUNCED，BIDDING_ENDED 前置 BIDDING
        // BIDDING_STARTED：PREPARING 未就绪（保留等待），ANNOUNCED 就绪，BIDDING/PENDING_OPEN/终态已过（SKIP）
        assertEquals(false, FlowTransitionServiceImpl.isEventPassed("BIDDING_STARTED", "PREPARING"));
        assertEquals(false, FlowTransitionServiceImpl.isEventPassed("BIDDING_STARTED", "ANNOUNCED"));
        assertEquals(true, FlowTransitionServiceImpl.isEventPassed("BIDDING_STARTED", "BIDDING"));
        assertEquals(true, FlowTransitionServiceImpl.isEventPassed("BIDDING_STARTED", "PENDING_OPEN"));
        assertEquals(true, FlowTransitionServiceImpl.isEventPassed("BIDDING_STARTED", "TERMINATED"));
        // BIDDING_ENDED：ANNOUNCED 未就绪，BIDDING 就绪，PENDING_OPEN 及之后已过
        assertEquals(false, FlowTransitionServiceImpl.isEventPassed("BIDDING_ENDED", "ANNOUNCED"));
        assertEquals(false, FlowTransitionServiceImpl.isEventPassed("BIDDING_ENDED", "BIDDING"));
        assertEquals(true, FlowTransitionServiceImpl.isEventPassed("BIDDING_ENDED", "PENDING_OPEN"));
        assertEquals(true, FlowTransitionServiceImpl.isEventPassed("BIDDING_ENDED", "COMPLETED"));
        assertEquals(true, FlowTransitionServiceImpl.isEventPassed("BIDDING_ENDED", null)); // 实体不存在 → 过时
        // 未知事件不重试
        assertEquals(true, FlowTransitionServiceImpl.isEventPassed("UNKNOWN", "BIDDING"));
    }

    @Test
    void resolve_shouldRejectUnknownTypeOrNull() {
        assertNull(FlowTransitionServiceImpl.resolveTarget("XXX", "DRAFT", "CANCEL"));
        assertNull(FlowTransitionServiceImpl.resolveTarget("ANNOUNCEMENT", "DRAFT", "PUBLISH")); // 公告走既有公告服务
        assertNull(FlowTransitionServiceImpl.resolveTarget(null, "DRAFT", "CANCEL"));
        assertNull(FlowTransitionServiceImpl.resolveTarget("PROJECT", null, "CANCEL"));
        assertNull(FlowTransitionServiceImpl.resolveTarget("PROJECT", "DRAFT", null));
    }
}
