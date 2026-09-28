package cn.iocoder.txgy.module.bidding.service.project;

// [依据] 契约《后端变更请求-追溯公告标段级关联-2026-10-02》A 部分：
//       ProjectTraceRespVO.notices（内部公告项）补 bidSectionCode（未关联标段=null）；
//       顺带补 tenderProjectCode（前端 TraceNotice 已声明、后端此前漏填，见 2026-09-23 三级编号扇出契约 1.2 节）。

import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.ProjectTraceRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.filing.FilingMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticefollow.FollowItemMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.BidSectionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.ProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.TenderProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectAuthMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import cn.iocoder.txgy.module.bidding.service.attachment.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * 项目全景追溯 - 内部公告项（NoticeItem）字段填充测试：
 * bidSectionCode / tenderProjectCode 从权威数据集快照 fieldsJson 解析，未关联标段返回 null。
 */
class ProjectTraceServiceNoticeItemTest {

    private ProjectTraceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProjectTraceServiceImpl();
        ReflectionTestUtils.setField(service, "projectMapper", mock(ProjectMapper.class));
        ReflectionTestUtils.setField(service, "tenderProjectMapper", mock(TenderProjectMapper.class));
        ReflectionTestUtils.setField(service, "bidSectionMapper", mock(BidSectionMapper.class));
        ReflectionTestUtils.setField(service, "noticeMapper", mock(NoticeMapper.class));
        ReflectionTestUtils.setField(service, "noticeExternalMapper", mock(NoticeExternalMapper.class));
        ReflectionTestUtils.setField(service, "filingMapper", mock(FilingMapper.class));
        ReflectionTestUtils.setField(service, "followItemMapper", mock(FollowItemMapper.class));
        ReflectionTestUtils.setField(service, "subjectAuthMapper", mock(SubjectAuthMapper.class));
        ReflectionTestUtils.setField(service, "subjectProfileMapper", mock(SubjectProfileMapper.class));
        ReflectionTestUtils.setField(service, "attachmentService", mock(AttachmentService.class));
    }

    private NoticeDO notice(String fieldsJson) {
        NoticeDO n = new NoticeDO();
        n.setId(2101075039321550850L);
        n.setNoticeCode("B6100002026000005");
        n.setNoticeType("TENDER_NOTICE");
        n.setTitle("test");
        n.setProjectCode("I6106002026000001");
        n.setPublishTime(LocalDateTime.of(2026, 9, 19, 6, 25, 2));
        n.setFieldsJson(fieldsJson);
        return n;
    }

    @SuppressWarnings("unchecked")
    private ProjectTraceRespVO.NoticeItem toItem(NoticeDO n) {
        return (ProjectTraceRespVO.NoticeItem) ReflectionTestUtils.invokeMethod(service, "toNoticeItem", n);
    }

    @Test
    void toNoticeItem_fillsTenderAndSectionFromPayload() {
        NoticeDO n = notice("{\"TENDER_PROJECT_CODE\":\"I6106002026000001001\",\"BID_SECTION_CODES\":\"I6106002026000001001001\"}");
        ProjectTraceRespVO.NoticeItem item = toItem(n);
        assertEquals("I6106002026000001", item.getProjectCode());
        assertEquals("I6106002026000001001", item.getTenderProjectCode());
        assertEquals("I6106002026000001001001", item.getBidSectionCode());
    }

    @Test
    void toNoticeItem_multiSectionTakesFirst() {
        NoticeDO n = notice("{\"TENDER_PROJECT_CODE\":\"T1\",\"BID_SECTION_CODES\":\"S001;S002\"}");
        ProjectTraceRespVO.NoticeItem item = toItem(n);
        assertEquals("T1", item.getTenderProjectCode());
        assertEquals("S001", item.getBidSectionCode());
    }

    @Test
    void toNoticeItem_noPayload_isNull() {
        NoticeDO n = notice(null);
        ProjectTraceRespVO.NoticeItem item = toItem(n);
        assertNull(item.getTenderProjectCode());
        assertNull(item.getBidSectionCode());
    }

    @Test
    void toNoticeItem_emptySection_isNull() {
        NoticeDO n = notice("{\"TENDER_PROJECT_CODE\":\"T1\"}");
        ProjectTraceRespVO.NoticeItem item = toItem(n);
        assertEquals("T1", item.getTenderProjectCode());
        assertNull(item.getBidSectionCode());
    }

    @Test
    void firstSectionCode_supportsChineseSeparators() {
        assertEquals("S001", invokeFirst("S001；S002"));
        assertEquals("S001", invokeFirst("S001、S002"));
        assertEquals("S001", invokeFirst("S001,S002"));
        assertEquals("S001", invokeFirst("S001 S002"));
        assertNull(invokeFirst("  "));
        assertNull(invokeFirst(null));
    }

    private String invokeFirst(String codes) {
        return (String) ReflectionTestUtils.invokeMethod(service, "firstSectionCode", codes);
    }

    @Test
    void readNoticePayload_badJson_isNull() {
        LinkedHashMap<?, ?> payload =
                (LinkedHashMap<?, ?>) ReflectionTestUtils.invokeMethod(service, "readNoticePayload", "{oops");
        assertNull(payload);
    }
}
