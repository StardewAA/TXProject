package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationComplianceReviewDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStandardDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationAssetMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationComplianceReviewMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationPerformanceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStaffConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStandardMapper;
import cn.iocoder.txgy.module.bidding.enums.QualificationReviewTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标准库/达标核查服务规则测试（Mockito，TDD）：
 * 标准登记与版本判重；要素聚合对照标准——全员达标 PASS、注册建造师缺口 FAIL、四库核验缺口 RISK、
 * 资质/标准缺失报错、快照留痕与类型缺省 SELF。依据：《电子证件全流程管理_数据库设计.md》v3.3 表 15-2/15-6。
 */
class EnterpriseQualificationComplianceServiceImplTest {

    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    private QualificationStandardMapper qualificationStandardMapper;
    private QualificationStaffConfigMapper qualificationStaffConfigMapper;
    private QualificationPerformanceMapper qualificationPerformanceMapper;
    private QualificationAssetMapper qualificationAssetMapper;
    private QualificationComplianceReviewMapper qualificationComplianceReviewMapper;
    private EnterpriseQualificationComplianceServiceImpl service;

    @BeforeEach
    void setUp() {
        enterpriseQualificationMapper = mock(EnterpriseQualificationMapper.class);
        qualificationStandardMapper = mock(QualificationStandardMapper.class);
        qualificationStaffConfigMapper = mock(QualificationStaffConfigMapper.class);
        qualificationPerformanceMapper = mock(QualificationPerformanceMapper.class);
        qualificationAssetMapper = mock(QualificationAssetMapper.class);
        qualificationComplianceReviewMapper = mock(QualificationComplianceReviewMapper.class);
        service = new EnterpriseQualificationComplianceServiceImpl();
        ReflectionTestUtils.setField(service, "enterpriseQualificationMapper", enterpriseQualificationMapper);
        ReflectionTestUtils.setField(service, "qualificationStandardMapper", qualificationStandardMapper);
        ReflectionTestUtils.setField(service, "qualificationStaffConfigMapper", qualificationStaffConfigMapper);
        ReflectionTestUtils.setField(service, "qualificationPerformanceMapper", qualificationPerformanceMapper);
        ReflectionTestUtils.setField(service, "qualificationAssetMapper", qualificationAssetMapper);
        ReflectionTestUtils.setField(service, "qualificationComplianceReviewMapper", qualificationComplianceReviewMapper);
    }

    // ---------- 标准登记 ----------

    @Test
    void createStandard_ok_registers() {
        when(qualificationStandardMapper.selectByScopeVersion(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);
        when(qualificationStandardMapper.insert(any(QualificationStandardDO.class))).thenAnswer(inv -> {
            QualificationStandardDO s = inv.getArgument(0);
            s.setId(90L);
            return 1;
        });
        Long id = service.createStandard("CONSTRUCTION", "市政公用工程施工总承包", "一级", "建市〔2014〕159号",
                "{\"REGISTERED_BUILDER\":2}", null, null, null, LocalDate.of(2014, 1, 1), null, "国标表110");
        assertEquals(90L, id);
        ArgumentCaptor<QualificationStandardDO> cap = ArgumentCaptor.forClass(QualificationStandardDO.class);
        verify(qualificationStandardMapper).insert(cap.capture());
        assertEquals("建市〔2014〕159号", cap.getValue().getStdVersion());
        assertNull(cap.getValue().getEffectiveTo(), "长期有效缺省 NULL");
    }

    @Test
    void createStandard_duplicateVersionRejected() {
        when(qualificationStandardMapper.selectByScopeVersion(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new QualificationStandardDO());
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.createStandard("CONSTRUCTION", "总承包", "一级", "建市〔2014〕159号",
                        "{}", null, null, null, LocalDate.now(), null, null));
        assertEquals(Integer.valueOf(1_060_106_023), e.getCode(), "同版本应报 QUAL_STANDARD_VERSION_DUPLICATE");
        verify(qualificationStandardMapper, never()).insert(any(QualificationStandardDO.class));
    }

    // ---------- 达标核查 ----------

    @Test
    void runReview_allSatisfied_pass() {
        stubBase("{\"REGISTERED_BUILDER\":2,\"MID_TITLE\":3}", "{\"net_asset_min\":1000}",
                "{\"recent_general_min\":3}");
        when(qualificationStaffConfigMapper.selectActiveCountByRole(1L, LocalDate.now())).thenReturn(List.of(
                Map.of("role_key", "REGISTERED_BUILDER", "cnt", 2L),
                Map.of("role_key", "MID_TITLE", "cnt", 4L)));
        when(qualificationPerformanceMapper.countRecentGeneralByQual(eq(1L), any(LocalDate.class))).thenReturn(5L);
        when(qualificationPerformanceMapper.countPlatformVerifiedByQual(1L)).thenReturn(5L);
        when(qualificationAssetMapper.sumLatestNetAsset(1L)).thenReturn(new BigDecimal("5600"));
        when(qualificationComplianceReviewMapper.insert(any(QualificationComplianceReviewDO.class))).thenAnswer(inv -> {
            QualificationComplianceReviewDO r = inv.getArgument(0);
            r.setId(7L);
            return 1;
        });

        Long id = service.runComplianceReview(1L, null, 42L, null);

        assertEquals(7L, id);
        QualificationComplianceReviewDO row = reviewRow();
        assertEquals("PASS", row.getReviewResult());
        assertEquals("SELF", row.getReviewType(), "缺省自查 SELF");
        assertTrue(row.getElementSnapshot().contains("\"REGISTERED_BUILDER\":2"));
        assertEquals("[]", row.getDiffItems());
    }

    @Test
    void runReview_regBuilderShort_failWithDiff() {
        stubBase("{\"REGISTERED_BUILDER\":2}", "{\"net_asset_min\":1000}", "{\"recent_general_min\":3}");
        when(qualificationStaffConfigMapper.selectActiveCountByRole(1L, LocalDate.now())).thenReturn(List.of(
                Map.of("role_key", "REGISTERED_BUILDER", "cnt", 1L))); // 缺 1
        when(qualificationPerformanceMapper.countRecentGeneralByQual(eq(1L), any(LocalDate.class))).thenReturn(5L);
        when(qualificationPerformanceMapper.countPlatformVerifiedByQual(1L)).thenReturn(5L);
        when(qualificationAssetMapper.sumLatestNetAsset(1L)).thenReturn(new BigDecimal("5600"));
        when(qualificationComplianceReviewMapper.insert(any(QualificationComplianceReviewDO.class))).thenReturn(1);

        service.runComplianceReview(1L, QualificationReviewTypeEnum.DYNAMIC, 42L, 100L);

        QualificationComplianceReviewDO row = reviewRow();
        assertEquals("FAIL", row.getReviewResult(), "注册建造师缺口为硬门槛");
        assertEquals("DYNAMIC", row.getReviewType());
        assertEquals(100L, row.getLinkedApplyId());
        assertTrue(row.getDiffItems().contains("注册建造师不足：需 2 名（有效在册 1）"));
    }

    @Test
    void runReview_platformVerifiedGap_risk() {
        stubBase("{\"REGISTERED_BUILDER\":2}", "{\"net_asset_min\":1000}",
                "{\"recent_general_min\":3,\"platform_verified_min\":3}");
        when(qualificationStaffConfigMapper.selectActiveCountByRole(1L, LocalDate.now())).thenReturn(List.of(
                Map.of("role_key", "REGISTERED_BUILDER", "cnt", 2L)));
        when(qualificationPerformanceMapper.countRecentGeneralByQual(eq(1L), any(LocalDate.class))).thenReturn(3L);
        when(qualificationPerformanceMapper.countPlatformVerifiedByQual(1L)).thenReturn(1L); // 缺线上核验
        when(qualificationAssetMapper.sumLatestNetAsset(1L)).thenReturn(new BigDecimal("5600"));
        when(qualificationComplianceReviewMapper.insert(any(QualificationComplianceReviewDO.class))).thenReturn(1);

        service.runComplianceReview(1L, QualificationReviewTypeEnum.PRE_APPLY, 42L, null);

        QualificationComplianceReviewDO row = reviewRow();
        assertEquals("RISK", row.getReviewResult(), "四库核验缺口为风险项非硬门槛");
        assertTrue(row.getDiffItems().contains("四库一平台已核验业绩不足：需 3 项（现有 1，可线上补核验）"));
    }

    @Test
    void runReview_qualNotExists() {
        when(enterpriseQualificationMapper.selectById(99L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.runComplianceReview(99L, null, 1L, null));
        assertEquals(Integer.valueOf(1_060_106_015), e.getCode());
    }

    @Test
    void runReview_standardNotFound() {
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qualOf());
        when(qualificationStandardMapper.selectCurrentStandard(anyString(), anyString(), anyString(),
                any(LocalDate.class))).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.runComplianceReview(1L, null, 1L, null));
        assertEquals(Integer.valueOf(1_060_106_022), e.getCode(), "无现行标准应报 QUAL_STANDARD_NOT_FOUND");
    }

    // ---------- 工具 ----------

    private void stubBase(String reqStaff, String reqAssets, String reqPerformance) {
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qualOf());
        QualificationStandardDO std = new QualificationStandardDO();
        std.setId(90L);
        std.setStdVersion("建市〔2014〕159号");
        std.setReqStaff(reqStaff);
        std.setReqAssets(reqAssets);
        std.setReqPerformance(reqPerformance);
        when(qualificationStandardMapper.selectCurrentStandard(anyString(), anyString(), anyString(),
                any(LocalDate.class))).thenReturn(std);
    }

    private EnterpriseQualificationDO qualOf() {
        EnterpriseQualificationDO q = new EnterpriseQualificationDO();
        q.setId(1L);
        q.setEntId(1L);
        q.setQualSerial("CONSTRUCTION");
        q.setQualCategory("市政公用工程施工总承包");
        q.setQualGrade("一级");
        return q;
    }

    private QualificationComplianceReviewDO reviewRow() {
        ArgumentCaptor<QualificationComplianceReviewDO> cap = ArgumentCaptor.forClass(QualificationComplianceReviewDO.class);
        verify(qualificationComplianceReviewMapper).insert(cap.capture());
        return cap.getValue();
    }
}
