package cn.iocoder.txgy.module.bidding.controller.admin.contract;

import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.contract.ContractDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.contract.ContractMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.enums.contract.ContractCompareResultEnum;
import cn.iocoder.txgy.module.bidding.service.contract.ContractServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * **合同一致性比对契约测试（standalone，不依赖 Spring 容器/DB）**：以
 * 《电子招标投标全流程说明书》§11.3 第 2 条（合同关键字段与中标结果比对，不一致标红）、
 * §11.5（系统标红提示，招标人确认）为据。校验 save 登记合同时自动比对：
 * ① 合同价 = 中标结果公示金额 → compareResult=MATCH；
 * ② 合同价 ≠ 公示金额 → compareResult=MISMATCH（前端标红提示，不拦截登记）；
 * ③ 无中标结果公示 / 公示缺金额 / 金额非数字 / 标段不匹配 → NO_BASELINE；
 * ④ 公示按 TENDER_PROJECT_CODE + BID_SECTION_CODE 定位（最新发布优先）。
 */
class ContractCompareApiContractTest {

    private ContractMapper contractMapper;
    private NoticeMapper noticeMapper;
    private ContractServiceImpl service;

    @BeforeEach
    void setUp() {
        contractMapper = mock(ContractMapper.class);
        noticeMapper = mock(NoticeMapper.class);
        service = new ContractServiceImpl();
        ReflectionTestUtils.setField(service, "contractMapper", contractMapper);
        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
    }

    private NoticeDO winNotice(String tenderProjectCode, String bidSectionCode, String bidAmount) {
        NoticeDO n = new NoticeDO();
        n.setId(1L);
        n.setNoticeType("WIN_RESULT_PUBLICITY");
        n.setStatus("PUBLISHED");
        n.setProjectCode(tenderProjectCode);
        n.setPublishTime(LocalDateTime.of(2026, 9, 25, 9, 0));
        // fieldsJson 照录规范英文名（与 NoticePayloadUtils canonicalize 输出一致）
        n.setFieldsJson("{\"TENDER_PROJECT_CODE\":\"" + tenderProjectCode + "\",\"BID_SECTION_CODE\":\"" + bidSectionCode + "\",\"BID_AMOUNT\":\"" + bidAmount + "\"}");
        return n;
    }

    private ContractSaveReqVO sampleReq(String projectCode, String sectionCode, String price) {
        ContractSaveReqVO req = new ContractSaveReqVO();
        req.setTenderProjectCode(projectCode);
        req.setBidSectionCode(sectionCode);
        req.setContractCode("C-COMPARE-001");
        req.setContractName("某项目施工合同");
        req.setContractPrice(new BigDecimal(price));
        return req;
    }

    @Test
    @DisplayName("合同价=中标结果金额 → compareResult=MATCH")
    void save_priceMatches() {
        when(noticeMapper.selectList(any())).thenReturn(List.of(
                winNotice("P1001", "S1001001", "1000000.00")));
        ContractDO inserted = saveAndCapture(sampleReq("P1001", "S1001001", "1000000.00"));
        assertEquals(ContractCompareResultEnum.MATCH.name(), inserted.getCompareResult(),
                "金额一致应标 MATCH");
    }

    @Test
    @DisplayName("合同价≠中标结果金额 → compareResult=MISMATCH（标红提示，不拦截）")
    void save_priceMismatch() {
        when(noticeMapper.selectList(any())).thenReturn(List.of(
                winNotice("P1002", "S1001002", "1000000.00")));
        ContractDO inserted = saveAndCapture(sampleReq("P1002", "S1001002", "900000.00"));
        assertEquals(ContractCompareResultEnum.MISMATCH.name(), inserted.getCompareResult(),
                "金额不一致应标 MISMATCH（前端标红提示，登记不拦截）");
    }

    @Test
    @DisplayName("无中标结果公示 → NO_BASELINE")
    void save_noWinNotice() {
        when(noticeMapper.selectList(any())).thenReturn(List.of());
        ContractDO inserted = saveAndCapture(sampleReq("P1003", "S1001003", "1000000.00"));
        assertEquals(ContractCompareResultEnum.NO_BASELINE.name(), inserted.getCompareResult(),
                "无公示基准应标 NO_BASELINE");
    }

    @Test
    @DisplayName("公示缺金额/金额非数字 → NO_BASELINE（容错）")
    void save_badAmount() {
        NoticeDO noAmount = winNotice("P1004", "S1001004", "1000000.00");
        noAmount.setFieldsJson("{\"TENDER_PROJECT_CODE\":\"P1004\",\"BID_SECTION_CODE\":\"S1001004\"}");
        when(noticeMapper.selectList(any())).thenReturn(List.of(noAmount));
        ContractDO a = saveAndCapture(sampleReq("P1004", "S1001004", "1000000.00"));
        assertEquals(ContractCompareResultEnum.NO_BASELINE.name(), a.getCompareResult(), "缺金额应 NO_BASELINE");

        NoticeDO badAmount = winNotice("P1004b", "S1001004b", "abc");
        when(noticeMapper.selectList(any())).thenReturn(List.of(badAmount));
        ContractDO b = saveAndCapture(sampleReq("P1004b", "S1001004b", "1000000.00"));
        assertEquals(ContractCompareResultEnum.NO_BASELINE.name(), b.getCompareResult(), "金额非数字应 NO_BASELINE");
    }

    @Test
    @DisplayName("公示标段与合同标段不匹配 → 跳过该公示（NO_BASELINE）")
    void save_sectionMismatchSkipped() {
        when(noticeMapper.selectList(any())).thenReturn(List.of(
                winNotice("P1005", "OTHER-SECTION", "1000000.00")));
        ContractDO inserted = saveAndCapture(sampleReq("P1005", "S1001005", "1000000.00"));
        assertEquals(ContractCompareResultEnum.NO_BASELINE.name(), inserted.getCompareResult(),
                "标段不一致应跳过（防止误用他标段中标金额）");
    }

    @Test
    @DisplayName("无标段合同（项目级）：公示有标段时仍按项目匹配")
    void save_projectLevelContract() {
        when(noticeMapper.selectList(any())).thenReturn(List.of(
                winNotice("P1006", "S1001006", "2000000.00")));
        // bidSectionCode 为空=项目级合同，不强制标段匹配
        ContractSaveReqVO req = sampleReq("P1006", null, "2000000.00");
        ContractDO inserted = saveAndCapture(req);
        assertEquals(ContractCompareResultEnum.MATCH.name(), inserted.getCompareResult(),
                "项目级合同（无标段）不应强制标段口径");
    }

    @Test
    @DisplayName("缺合同价 → NO_BASELINE（容错）")
    void save_missingPrice() {
        when(noticeMapper.selectList(any())).thenReturn(List.of(
                winNotice("P1007", "S1001007", "1000000.00")));
        ContractSaveReqVO req = sampleReq("P1007", "S1001007", "1000000.00");
        req.setContractPrice(null);
        ContractDO inserted = saveAndCapture(req);
        assertEquals(ContractCompareResultEnum.NO_BASELINE.name(), inserted.getCompareResult(),
                "缺合同价应 NO_BASELINE");
    }

    @Test
    @DisplayName("契约：ContractRespVO 外发字段含 compareResult（前端标红消费）")
    void respVO_containsCompareResult() {
        boolean found = false;
        for (java.lang.reflect.Field f : cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractRespVO.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getName().equals("compareResult")) {
                found = true;
            }
        }
        assertNotNull(found ? "compareResult" : null, "RespVO 应含 compareResult（前端据此标红）");
    }

    // ===== 辅助：调用 save 并捕获 insert 入参 =====

    private ContractDO saveAndCapture(ContractSaveReqVO req) {
        org.mockito.Mockito.clearInvocations(contractMapper); // 清掉历史调用，验证本次 insert 恰好 1 次
        service.save(req);
        org.mockito.ArgumentCaptor<ContractDO> captor = org.mockito.ArgumentCaptor.forClass(ContractDO.class);
        org.mockito.Mockito.verify(contractMapper).insert(captor.capture());
        return captor.getValue();
    }
}
