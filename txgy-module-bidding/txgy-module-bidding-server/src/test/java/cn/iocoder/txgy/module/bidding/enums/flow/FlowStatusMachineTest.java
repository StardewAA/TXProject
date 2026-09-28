package cn.iocoder.txgy.module.bidding.enums.flow;

import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 招标全流程状态机流转规则锁定（四实体生命周期，见 docs/前端规范-全域接口说明/26-招标全流程状态机设计）。
 *
 * <p>为什么只测状态机：流转规则是全流程的骨架，一旦放错（如允许跳阶段、终态可复活），
 * 招标项目的合规性（法定程序顺序）即被破坏；规则在枚举层集中校验、服务层动作方法再引用。
 */
class FlowStatusMachineTest {

    // ==================== 项目 Project ====================

    @Test
    void project_shouldTransitMainChain() {
        assertTrue(ProjectStatusEnum.canTransit(ProjectStatusEnum.DRAFT, ProjectStatusEnum.APPROVING));
        assertTrue(ProjectStatusEnum.canTransit(ProjectStatusEnum.APPROVING, ProjectStatusEnum.ACTIVE));
        assertTrue(ProjectStatusEnum.canTransit(ProjectStatusEnum.ACTIVE, ProjectStatusEnum.COMPLETED));
        // 取消：草稿/审批中/已立项均可取消
        assertTrue(ProjectStatusEnum.canTransit(ProjectStatusEnum.DRAFT, ProjectStatusEnum.CANCELLED));
        assertTrue(ProjectStatusEnum.canTransit(ProjectStatusEnum.APPROVING, ProjectStatusEnum.CANCELLED));
        assertTrue(ProjectStatusEnum.canTransit(ProjectStatusEnum.ACTIVE, ProjectStatusEnum.CANCELLED));
    }

    @Test
    void project_shouldRejectIllegalAndTerminal() {
        assertFalse(ProjectStatusEnum.canTransit(ProjectStatusEnum.DRAFT, ProjectStatusEnum.ACTIVE)); // 跳审批
        assertFalse(ProjectStatusEnum.canTransit(ProjectStatusEnum.ACTIVE, ProjectStatusEnum.APPROVING)); // 已立项回审批
        assertFalse(ProjectStatusEnum.canTransit(ProjectStatusEnum.COMPLETED, ProjectStatusEnum.ACTIVE)); // 终态
        assertFalse(ProjectStatusEnum.canTransit(ProjectStatusEnum.CANCELLED, ProjectStatusEnum.DRAFT)); // 终态
        assertTrue(ProjectStatusEnum.COMPLETED.isTerminal());
        assertTrue(ProjectStatusEnum.CANCELLED.isTerminal());
    }

    // ==================== 招标项目 Tender（最核心） ====================

    @Test
    void tender_shouldTransitMainChain() {
        TenderStatusEnum[] chain = {
                TenderStatusEnum.DRAFT, TenderStatusEnum.APPROVING, TenderStatusEnum.PREPARING,
                TenderStatusEnum.ANNOUNCED, TenderStatusEnum.BIDDING, TenderStatusEnum.PENDING_OPEN,
                TenderStatusEnum.EVALUATING, TenderStatusEnum.PUBLICIZING, TenderStatusEnum.AWARDED,
                TenderStatusEnum.CONTRACTING, TenderStatusEnum.COMPLETED
        };
        for (int i = 0; i < chain.length - 1; i++) {
            assertTrue(TenderStatusEnum.canTransit(chain[i], chain[i + 1]), chain[i] + "→" + chain[i + 1] + " 应允许");
        }
    }

    @Test
    void tender_shouldSupportRejectReconsiderAndTerminate() {
        assertTrue(TenderStatusEnum.canTransit(TenderStatusEnum.APPROVING, TenderStatusEnum.DRAFT)); // 审批驳回退回
        assertTrue(TenderStatusEnum.canTransit(TenderStatusEnum.PUBLICIZING, TenderStatusEnum.EVALUATING)); // 异议成立复核
        // 任意非终态可终止（招标方主动终止/全部流标），已归档除外
        assertTrue(TenderStatusEnum.canTransit(TenderStatusEnum.PREPARING, TenderStatusEnum.TERMINATED));
        assertTrue(TenderStatusEnum.canTransit(TenderStatusEnum.BIDDING, TenderStatusEnum.TERMINATED));
        assertTrue(TenderStatusEnum.canTransit(TenderStatusEnum.EVALUATING, TenderStatusEnum.TERMINATED));
        assertTrue(TenderStatusEnum.canTransit(TenderStatusEnum.PUBLICIZING, TenderStatusEnum.TERMINATED));
    }

    @Test
    void tender_shouldRejectIllegalAndTerminal() {
        assertFalse(TenderStatusEnum.canTransit(TenderStatusEnum.DRAFT, TenderStatusEnum.PREPARING)); // 跳审批
        assertFalse(TenderStatusEnum.canTransit(TenderStatusEnum.ANNOUNCED, TenderStatusEnum.EVALUATING)); // 跳阶段
        assertFalse(TenderStatusEnum.canTransit(TenderStatusEnum.BIDDING, TenderStatusEnum.AWARDED)); // 未评标先定标
        assertFalse(TenderStatusEnum.canTransit(TenderStatusEnum.COMPLETED, TenderStatusEnum.TERMINATED)); // 归档不可终止
        assertFalse(TenderStatusEnum.canTransit(TenderStatusEnum.TERMINATED, TenderStatusEnum.DRAFT)); // 终态无出口
        assertTrue(TenderStatusEnum.COMPLETED.isTerminal());
        assertTrue(TenderStatusEnum.TERMINATED.isTerminal());
    }

    // ==================== 标段 BidSection ====================

    @Test
    void section_shouldTransitMainChainAndFail() {
        assertTrue(BidSectionStatusEnum.canTransit(BidSectionStatusEnum.DRAFT, BidSectionStatusEnum.BIDDING));
        assertTrue(BidSectionStatusEnum.canTransit(BidSectionStatusEnum.BIDDING, BidSectionStatusEnum.EVALUATING));
        assertTrue(BidSectionStatusEnum.canTransit(BidSectionStatusEnum.BIDDING, BidSectionStatusEnum.FAILED)); // 不足3家
        assertTrue(BidSectionStatusEnum.canTransit(BidSectionStatusEnum.EVALUATING, BidSectionStatusEnum.AWARDED));
        assertTrue(BidSectionStatusEnum.canTransit(BidSectionStatusEnum.EVALUATING, BidSectionStatusEnum.FAILED)); // 全部废标
        assertTrue(BidSectionStatusEnum.canTransit(BidSectionStatusEnum.AWARDED, BidSectionStatusEnum.COMPLETED));
    }

    @Test
    void section_shouldRejectIllegal() {
        assertFalse(BidSectionStatusEnum.canTransit(BidSectionStatusEnum.DRAFT, BidSectionStatusEnum.EVALUATING)); // 跳招标
        assertFalse(BidSectionStatusEnum.canTransit(BidSectionStatusEnum.FAILED, BidSectionStatusEnum.BIDDING)); // 终态（重新招标=新建招标项目）
        assertFalse(BidSectionStatusEnum.canTransit(BidSectionStatusEnum.COMPLETED, BidSectionStatusEnum.AWARDED)); // 终态
        assertTrue(BidSectionStatusEnum.FAILED.isTerminal());
        assertTrue(BidSectionStatusEnum.COMPLETED.isTerminal());
    }

    // ==================== 公告 Announcement ====================

    @Test
    void announcement_shouldTransit() {
        assertTrue(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.DRAFT, AnnouncementStatusEnum.REVIEWING));
        assertTrue(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.DRAFT, AnnouncementStatusEnum.PUBLISHED)); // 测试模式免审直发
        assertTrue(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.REVIEWING, AnnouncementStatusEnum.PUBLISHED));
        assertTrue(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.REVIEWING, AnnouncementStatusEnum.DRAFT)); // 驳回退回
        assertTrue(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.PUBLISHED, AnnouncementStatusEnum.WITHDRAWN));
        assertTrue(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.PUBLISHED, AnnouncementStatusEnum.EXPIRED)); // 到期
        assertTrue(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.WITHDRAWN, AnnouncementStatusEnum.DRAFT)); // 撤回后修改重发
    }

    @Test
    void announcement_shouldRejectIllegal() {
        assertFalse(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.DRAFT, AnnouncementStatusEnum.WITHDRAWN)); // 未发布不能撤回
        assertFalse(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.WITHDRAWN, AnnouncementStatusEnum.PUBLISHED)); // 撤回后须回草稿
        assertFalse(AnnouncementStatusEnum.canTransit(AnnouncementStatusEnum.EXPIRED, AnnouncementStatusEnum.DRAFT)); // 终态
        assertTrue(AnnouncementStatusEnum.EXPIRED.isTerminal());
    }

    // ==================== 公告类型映射 ====================

    @Test
    void announcementType_shouldMapToExistingNoticeType() {
        assertEquals(NoticeTypeEnum.TENDER_NOTICE, AnnouncementTypeEnum.TENDER_NOTICE.getNoticeType());
        assertEquals(NoticeTypeEnum.CHANGE_NOTICE, AnnouncementTypeEnum.CHANGE_NOTICE.getNoticeType());
        assertEquals(NoticeTypeEnum.WIN_CANDIDATE_PUBLICITY, AnnouncementTypeEnum.CANDIDATE_PUBLICITY.getNoticeType());
        assertEquals(NoticeTypeEnum.WIN_RESULT_PUBLICITY, AnnouncementTypeEnum.RESULT_NOTICE.getNoticeType());
        assertNull(AnnouncementTypeEnum.TERMINATION_NOTICE.getNoticeType()); // 数据集未登记
    }

    @Test
    void of_shouldResolveByCode() {
        assertEquals(ProjectStatusEnum.ACTIVE, ProjectStatusEnum.of("ACTIVE"));
        assertEquals(TenderStatusEnum.EVALUATING, TenderStatusEnum.of("EVALUATING"));
        assertNull(TenderStatusEnum.of("NOPE"));
        assertEquals(FlowModeEnum.TEST, FlowModeEnum.of("TEST"));
        assertNull(FlowModeEnum.of("NOPE"));
    }
}
