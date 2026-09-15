package cn.iocoder.txgy.module.bidding.service.filing;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingResultReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingSubmitReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.filing.FilingDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.filing.FilingMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.FilingStatusEnum;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectEligibilityService;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 备案服务规则测试（Mockito）：幂等命中/受理回执/监管回推状态机。
 */
class FilingServiceImplTest {

    private FilingMapper filingMapper;
    private SubjectEligibilityService subjectEligibilityService;
    private FilingServiceImpl service;

    @BeforeEach
    void setUp() {
        filingMapper = mock(FilingMapper.class);
        subjectEligibilityService = mock(SubjectEligibilityService.class);
        service = new FilingServiceImpl();
        ReflectionTestUtils.setField(service, "filingMapper", filingMapper);
        ReflectionTestUtils.setField(service, "subjectEligibilityService", subjectEligibilityService);
    }

    @Test
    void submit_createsPendingWithReceiptNo() {
        when(filingMapper.selectOne(any())).thenReturn(null);
        when(filingMapper.insert(any(FilingDO.class))).thenAnswer(inv -> 1);

        var resp = service.submitFiling(req("NOTICE_FILING", "P-2026-001"));

        assertEquals(FilingStatusEnum.PENDING.name(), resp.getStatus());
        assertTrue(resp.getReceiptNo().startsWith("FB"), "应生成受理号");
        ArgumentCaptor<FilingDO> captor = ArgumentCaptor.forClass(FilingDO.class);
        verify(filingMapper).insert(captor.capture());
        assertEquals("P-2026-001", captor.getValue().getProjectCode());
    }

    @Test
    void submit_duplicatePowerKeyRejected() {
        when(filingMapper.selectOne(any())).thenReturn(filingOf(FilingStatusEnum.PENDING));
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.submitFiling(req("NOTICE_FILING", "P-2026-001")));
        assertEquals(Integer.valueOf(1_060_101_010), e.getCode(), "幂等命中应报 FILING_ALREADY_SUBMITTED");
    }

    @Test
    void receive_pendingToAccepted() {
        FilingDO filing = filingOf(FilingStatusEnum.PENDING);
        when(filingMapper.selectOne(any())).thenReturn(filing);
        FilingResultReqVO reqVO = new FilingResultReqVO();
        reqVO.setReceiptNo("FB-X");
        reqVO.setResultStatus(FilingStatusEnum.ACCEPTED.name());
        reqVO.setOpinion("同意备案");

        service.receiveFilingResult(reqVO);

        verify(filingMapper).updateById(any(FilingDO.class));
        assertEquals(FilingStatusEnum.ACCEPTED.name(), filing.getStatus());
    }

    @Test
    void receive_rejectsFromTerminalState() {
        FilingDO filing = filingOf(FilingStatusEnum.ACCEPTED);
        when(filingMapper.selectOne(any())).thenReturn(filing);
        FilingResultReqVO reqVO = new FilingResultReqVO();
        reqVO.setReceiptNo("FB-X");
        reqVO.setResultStatus(FilingStatusEnum.REJECTED.name());

        ServiceException e = assertThrows(ServiceException.class, () -> service.receiveFilingResult(reqVO));
        assertEquals(Integer.valueOf(1_060_101_012), e.getCode(), "终态不可再回推");
    }

    @Test
    void receive_notFound() {
        when(filingMapper.selectOne(any())).thenReturn(null);
        FilingResultReqVO reqVO = new FilingResultReqVO();
        reqVO.setReceiptNo("FB-不存在");
        reqVO.setResultStatus(FilingStatusEnum.ACCEPTED.name());
        ServiceException e = assertThrows(ServiceException.class, () -> service.receiveFilingResult(reqVO));
        assertEquals(Integer.valueOf(1_060_101_011), e.getCode(), "应报 FILING_NOT_EXISTS");
    }


    // ========== 招标人发布门（备案提交） ==========

    @Test
    void submit_blocksWhenTendererNotAuthorized() {
        // 备案由招标人（或委托代理）提出（招标投标法第12条第3款/实施条例第15条）；平台未授权 → 106_031 且不落库
        ServiceException ex = ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_NOT_AUTHORIZED);
        org.mockito.Mockito.doThrow(ex).when(subjectEligibilityService).assertTendererIssuanceAllowed();

        assertThrows(ServiceException.class, () -> service.submitFiling(req("NOTICE_FILING", "P-2026-GATE")));
        org.mockito.Mockito.verify(filingMapper, org.mockito.Mockito.never()).insert(any(FilingDO.class));
    }

    // ========== 工具 ==========

    private FilingSubmitReqVO req(String type, String projectCode) {
        FilingSubmitReqVO reqVO = new FilingSubmitReqVO();
        reqVO.setFilingType(type);
        reqVO.setProjectCode(projectCode);
        reqVO.setUnifiedDealCode("E-UNIFIED-001");
        return reqVO;
    }

    private FilingDO filingOf(FilingStatusEnum status) {
        FilingDO filing = new FilingDO();
        filing.setId(1L);
        filing.setFilingType("NOTICE_FILING");
        filing.setProjectCode("P-2026-001");
        filing.setReceiptNo("FB-X");
        filing.setStatus(status.name());
        return filing;
    }
}
