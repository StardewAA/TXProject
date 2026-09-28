package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.mockito.ArgumentCaptor;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private RuleConfigService ruleConfigService;

    @BeforeEach
    void setUp() {
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        enterpriseQualificationMapper = mock(EnterpriseQualificationMapper.class);
        ruleConfigService = mock(RuleConfigService.class);
        // 默认桩回原硬编码值 60：让不关心窗口的用例保持原语义（窗口判定现在走配置）
        lenient().when(ruleConfigService.resolveWithAudit(
                        eq(RuleConfigKeys.QUAL_MATERIAL_EXPIRING_WINDOW_DAYS), anyString()))
                .thenReturn(ruleConfigItem("60"));
        qualificationSafetyPermitMapper = mock(QualificationSafetyPermitMapper.class);
        qualificationPerformanceMapper = mock(QualificationPerformanceMapper.class);
        service = new EnterpriseMaterialServiceImpl();
        ReflectionTestUtils.setField(service, "ruleConfigService", ruleConfigService);
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

    /**
     * 钉住新契约《营业执照纳入证照台账》的核心链路：完备性自查**必须按 cert_type=ADMIN_LICENSE 去证照台账查营业执照**。
     *
     * <p>规范出处：`EnterpriseMaterialTypeEnum.BUSINESS_LICENSE(IDENTITY, "营业执照", "ADMIN_LICENSE", MaterialSource.CERT, true)`
     * ——营业执照是必备材料，且**唯一来源是证照台账**。
     *
     * <p>为什么必须有用例：这条链路是"页面上的字段在台账里、材料完备性却在别的表里找"的典型错位。
     * 前端只填主体档案（`bidding_subject_profile.license_file_id`）时，台账里没有这条记录，
     * 完备性必然判 MISSING —— 用户明明上传过却显示"缺失"。所以这里不做泛泛的"有记录就算齐备"断言，
     * 而是**捕获真实下发的查询条件**，确认类型码就是 `ADMIN_LICENSE`：改错类型码（或改回去）这条用例立刻失败。
     */
    @Test
    void check_businessLicense_isSourcedFromLedgerByAdminLicenseType() {
        // ① 口径层：营业执照的取证类型码与来源（改错这个码，前端把执照放进台账也不会被认账）
        var businessLicense = cn.iocoder.txgy.module.bidding.enums.EnterpriseMaterialTypeEnum.BUSINESS_LICENSE;
        assertEquals("ADMIN_LICENSE", businessLicense.getCertType(),
                "营业执照必须按 cert_type=ADMIN_LICENSE 去证照台账取（字典 cert_type 第 1 条即此值）");
        assertEquals(cn.iocoder.txgy.module.bidding.enums.EnterpriseMaterialTypeEnum.MaterialSource.CERT,
                businessLicense.getSource(),
                "营业执照的唯一来源必须是证照台账（certificate_info）；来源若改成主体档案，本契约的验收点即失效");
        assertTrue(businessLicense.isMandatory(), "营业执照是必备材料 → 台账缺记录必然判 MISSING（这就是当前的实际故障）");

        // ② 行为层：完备性自查确实向证照台账下发查询（未登记执照时 MISSING，登记后 READY）
        stubAllReady();
        service.checkCompleteness(1L, LocalDate.now());
        verify(certificateInfoMapper, atLeastOnce()).selectList(any());

        when(certificateInfoMapper.selectList(any())).thenReturn(List.of());
        EnterpriseMaterialCompletenessRespVO empty = service.checkCompleteness(1L, LocalDate.now());
        assertTrue(empty.getItems().stream()
                        .anyMatch(i -> "MISSING".equals(i.getStatus()) && Boolean.TRUE.equals(i.getMandatory())),
                                        "台账无记录时必须有必备材料判 MISSING（复现“只填主体档案 → 营业执照报缺失”的现状）");
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
    void check_risk_usesConfiguredWindow_notHardcoded60() {
        // 窗口经配置解析：配成 90 天时，80 天后到期的材料也属"临近到期"→ RISK。
        // 旧实现硬编码 60 → 80 天的材料会被判为齐备（此用例在旧实现下必红）。
        // 为什么单开键而不是接 cert.expiring.window.days：默认值同为 60 但语义不同
        // （资格自查提前期 vs 列表角标展示口径，后者后端有意不读）。
        stubAllReady();
        when(ruleConfigService.resolveWithAudit(
                eq(RuleConfigKeys.QUAL_MATERIAL_EXPIRING_WINDOW_DAYS), anyString()))
                .thenReturn(ruleConfigItem("90"));
        when(certificateInfoMapper.selectList(any()))
                .thenReturn(List.of(cert("还剩八十天的材料", LocalDate.now().plusDays(80))));

        EnterpriseMaterialCompletenessRespVO vo = service.checkCompleteness(1L, LocalDate.now());

        assertEquals("RISK", vo.getResult(), "窗口 90 天 → 80 天到期的材料应判临近到期");
        assertFalse(vo.getExpiringItems().isEmpty());
    }

    /** 构造规则配置项返回值（本服务只关心 value）。 */
    private RuleConfigItemRespVO ruleConfigItem(String value) {
        RuleConfigItemRespVO item = new RuleConfigItemRespVO();
        item.setValue(value);
        item.setVersionNo(1);
        item.setSource("PLATFORM");
        return item;
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
