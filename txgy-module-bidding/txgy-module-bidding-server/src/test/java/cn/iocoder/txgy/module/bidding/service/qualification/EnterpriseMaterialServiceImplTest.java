package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.EnterpriseMaterialCompletenessRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationPerformanceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 企业材料完备性自查测试（Mockito，TDD）。
 *
 * <p>依据：用户口径"按《资质管理规范文件》三分类覆盖企业身份证明、企业资质证明、企业实力证明"；
 * 《电子招标投标办法》（20号令）第42条；《建筑业企业资质管理规定》（住建部令第22号）+
 * 《安全生产许可证条例》（国务院令第397号）；v3.3 表 1/15-1/15-4/15-8；
 * 结论三档口径对齐 v3.3 表 15-6（PASS/RISK/FAIL）。
 * <p>覆盖：必备缺失 → FAIL 且列出缺项；仅临近到期 → RISK；全部齐备且在窗口外 → PASS；
 * 过期材料按缺失处理（不因"记录存在"而误判齐备）。
 */
class EnterpriseMaterialServiceImplTest {

    private CertificateInfoMapper certificateInfoMapper;
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    private QualificationPerformanceMapper qualificationPerformanceMapper;
    private EnterpriseMaterialServiceImpl service;

    @BeforeEach
    void setUp() {
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        enterpriseQualificationMapper = mock(EnterpriseQualificationMapper.class);
        qualificationSafetyPermitMapper = mock(QualificationSafetyPermitMapper.class);
        qualificationPerformanceMapper = mock(QualificationPerformanceMapper.class);
        service = new EnterpriseMaterialServiceImpl();
        ReflectionTestUtils.setField(service, "certificateInfoMapper", certificateInfoMapper);
        ReflectionTestUtils.setField(service, "enterpriseQualificationMapper", enterpriseQualificationMapper);
        ReflectionTestUtils.setField(service, "qualificationSafetyPermitMapper", qualificationSafetyPermitMapper);
        ReflectionTestUtils.setField(service, "qualificationPerformanceMapper", qualificationPerformanceMapper);
    }

    private CertificateInfoDO cert(String name, LocalDate validTo) {
        CertificateInfoDO c = new CertificateInfoDO();
        c.setId(1L);
        c.setCertName(name);
        c.setCertStatus("ACTIVE");
        c.setValidTo(validTo);
        return c;
    }

    /** 让"资料齐备（长期有效）"成为基线，再按用例局部破坏。 */
    private void stubAllReady() {
        when(certificateInfoMapper.selectList(any())).thenReturn(List.of(cert("材料", null)));
        EnterpriseQualificationDO qual = new EnterpriseQualificationDO();
        qual.setId(2L);
        qual.setCertNo("D123456789");
        qual.setQualGrade("一级");
        qual.setQualCategory("市政公用工程施工总承包");
        qual.setValidTo(null);
        when(enterpriseQualificationMapper.selectList(any())).thenReturn(List.of(qual));
        QualificationSafetyPermitDO permit = new QualificationSafetyPermitDO();
        permit.setId(3L);
        permit.setPermitNo("(陕)JZ安许证字[2026]000001");
        permit.setValidTo(null);
        when(qualificationSafetyPermitMapper.selectList(any())).thenReturn(List.of(permit));
        when(qualificationPerformanceMapper.selectCount(any())).thenReturn(3L);
    }

    @Test
    void check_pass_whenAllMandatoryReady() {
        stubAllReady();

        EnterpriseMaterialCompletenessRespVO vo = service.checkCompleteness(1L, LocalDate.now());

        assertEquals("PASS", vo.getResult());
        assertTrue(vo.getMissingItems().isEmpty());
        assertTrue(vo.getExpiringItems().isEmpty());
        assertEquals(vo.getRequiredCount(), vo.getReadyCount());
    }

    @Test
    void check_fail_whenMandatoryMaterialMissing() {
        stubAllReady();
        // 台账中该 cert_type 无记录 → 必备材料缺失
        when(certificateInfoMapper.selectList(any())).thenReturn(List.of());

        EnterpriseMaterialCompletenessRespVO vo = service.checkCompleteness(1L, LocalDate.now());

        assertEquals("FAIL", vo.getResult());
        assertFalse(vo.getMissingItems().isEmpty());
        assertTrue(vo.getItems().stream()
                .anyMatch(i -> "MISSING".equals(i.getStatus()) && Boolean.TRUE.equals(i.getMandatory())));
    }

    @Test
    void check_fail_whenMaterialExpired_evenIfRecordExists() {
        // 为什么这条重要：只判"记录存在"会把已过期的资质当成齐备，直接导致资格审查被否决
        stubAllReady();
        when(certificateInfoMapper.selectList(any()))
                .thenReturn(List.of(cert("已过期材料", LocalDate.now().minusDays(1))));

        EnterpriseMaterialCompletenessRespVO vo = service.checkCompleteness(1L, LocalDate.now());

        assertEquals("FAIL", vo.getResult());
        assertTrue(vo.getItems().stream().anyMatch(i -> "MISSING".equals(i.getStatus())
                && i.getMessage() != null && i.getMessage().contains("均已过期")));
    }

    @Test
    void check_risk_whenMaterialExpiringWithinWindow() {
        stubAllReady();
        when(certificateInfoMapper.selectList(any()))
                .thenReturn(List.of(cert("即将到期材料", LocalDate.now().plusDays(10))));

        EnterpriseMaterialCompletenessRespVO vo = service.checkCompleteness(1L, LocalDate.now());

        assertEquals("RISK", vo.getResult());
        assertTrue(vo.getMissingItems().isEmpty());
        assertFalse(vo.getExpiringItems().isEmpty());
    }

    @Test
    void check_usesAsOfDate_forExpiryJudgement() {
        // 基准日可回填：便于"以投标截止日为基准"做历史自查
        stubAllReady();
        LocalDate validTo = LocalDate.now().plusDays(5);
        when(certificateInfoMapper.selectList(any())).thenReturn(List.of(cert("材料", validTo)));

        EnterpriseMaterialCompletenessRespVO vo = service.checkCompleteness(1L, validTo.plusDays(1));

        assertEquals("FAIL", vo.getResult());
    }
}
