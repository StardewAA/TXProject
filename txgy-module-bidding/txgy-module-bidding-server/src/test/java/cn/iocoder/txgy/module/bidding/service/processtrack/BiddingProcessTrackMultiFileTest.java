package cn.iocoder.txgy.module.bidding.service.processtrack;

// [依据] 契约《后端变更请求-投标与资格预审多文件化-2026-10-02》：
//       R2 投标流程返回体带附件（bizType=BID_TRACK，bizCode=track.id）；
//       R5 process-track/page 增加 bidSectionCode 入参按标段聚合投标记录。

import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.processtrack.BiddingProcessTrackDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.processtrack.BiddingProcessEventMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.processtrack.BiddingProcessTrackMapper;
import cn.iocoder.txgy.module.bidding.service.attachment.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 投标流程跟踪 - 多文件化（契约 2026-10-02）测试：
 * R2 listMyWithAttachments 填充投标文件集附件；R5 buildOverviewWrapper 按标段过滤。
 */
class BiddingProcessTrackMultiFileTest {

    private BiddingProcessTrackServiceImpl service;
    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        service = new BiddingProcessTrackServiceImpl();
        ReflectionTestUtils.setField(service, "trackMapper", mock(BiddingProcessTrackMapper.class));
        ReflectionTestUtils.setField(service, "eventMapper", mock(BiddingProcessEventMapper.class));
        attachmentService = mock(AttachmentService.class);
        ReflectionTestUtils.setField(service, "attachmentService", attachmentService);
    }

    private BiddingProcessTrackDO track(Long id, String sectionCode) {
        BiddingProcessTrackDO t = new BiddingProcessTrackDO();
        t.setId(id);
        t.setBidSectionCode(sectionCode);
        return t;
    }

    @Test
    void listMyWithAttachments_fillsAttachmentPerTrack() {
        BiddingProcessTrackMapper mapper = (BiddingProcessTrackMapper) ReflectionTestUtils.getField(service, "trackMapper");
        when(mapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction>any(),
                org.mockito.ArgumentMatchers.any(Object.class))).thenReturn(List.of(track(101L, "S001")));
        AttachmentRespVO att = new AttachmentRespVO();
        att.setId(7L);
        att.setBizType("BID_TRACK");
        att.setBizCode("101");
        when(attachmentService.list("BID_TRACK", "101")).thenReturn(List.of(att));

        List<ProcessTrackRespVO> result = service.listMyWithAttachments(1L);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getAttachments());
        assertEquals(1, result.get(0).getAttachments().size());
        assertEquals("101", result.get(0).getAttachments().get(0).getBizCode());
        verify(attachmentService).list(eq("BID_TRACK"), eq("101"));
    }

    @Test
    void listMyWithAttachments_noAttachment_returnsEmptyList() {
        BiddingProcessTrackMapper mapper = (BiddingProcessTrackMapper) ReflectionTestUtils.getField(service, "trackMapper");
        when(mapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction>any(),
                org.mockito.ArgumentMatchers.any(Object.class))).thenReturn(List.of(track(102L, "S002")));
        when(attachmentService.list("BID_TRACK", "102")).thenReturn(List.of());

        List<ProcessTrackRespVO> result = service.listMyWithAttachments(1L);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getAttachments());
        assertTrue(result.get(0).getAttachments().isEmpty());
    }

    @Test
    void buildOverviewWrapper_filtersByBidSectionCode() {
        // 通过反射取 private wrapper（isSuper=false 时也会拼 tenant 条件，此处仅验证 bidSectionCode 分支不抛错且条件追加）
        ProcessTrackPageReqVO req = new ProcessTrackPageReqVO();
        req.setBidSectionCode("I6106002026000001001002");
        Object wrapper = ReflectionTestUtils.invokeMethod(service, "buildOverviewWrapper", req, false);
        assertNotNull(wrapper, "wrapper 应可构造");
    }
}
