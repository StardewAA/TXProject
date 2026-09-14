package cn.iocoder.txgy.module.bidding.service.safety;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.KeyPostChangeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyGraceRegisterReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyPermitRiskRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelMultiPostCheckDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.ProjectKeyPostAssignmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.SafetyPermitRiskSnapshotDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelJobChangeLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelMultiPostCheckMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.ProjectKeyPostAssignmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.SafetyPermitRiskSnapshotMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.SafetyRiskFlagEnum;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * 安许 A/B/C 联动风险服务测试（Mockito，无 DB 依赖）。
 *
 * <p>依据：用户业务口径（任一不达标→黄；任一为 0→红；全为 0→失效；一年预警 ≥4 次→频繁；
 * 法定代表人变更给 180 天宽限；劳务分包 B 证为 0 不触发红）；《电子资质七大模块技术方案.md》§1.2（判定顺序）、
 * §1.4（拦截）、§1.5（dryRun 上线策略）。
 * <p>覆盖：四态判定 + 豁免 + 宽限降档 + 失效宽限计时 + dryRun 不落库 + 宽限登记双校验 + 多岗持证校验 + 关键岗位变更计数。
 */
class SafetyPermitRiskServiceImplTest {

    private QualificationSafetyPermitMapper safetyPermitMapper;
    private PersonnelCertificateMapper personnelCertificateMapper;
    private PersonnelProfileMapper personnelProfileMapper;
    private PersonnelJobChangeLogMapper jobChangeLogMapper;
    private ProjectKeyPostAssignmentMapper keyPostAssignmentMapper;
    private PersonnelMultiPostCheckMapper multiPostCheckMapper;
    private SafetyPermitRiskSnapshotMapper snapshotMapper;
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    private RuleConfigService ruleConfigService;
    private SafetyPermitRiskServiceImpl service;

    @BeforeEach
    void setUp() {
        safetyPermitMapper = mock(QualificationSafetyPermitMapper.class);
        personnelCertificateMapper = mock(PersonnelCertificateMapper.class);
        personnelProfileMapper = mock(PersonnelProfileMapper.class);
        jobChangeLogMapper = mock(PersonnelJobChangeLogMapper.class);
        keyPostAssignmentMapper = mock(ProjectKeyPostAssignmentMapper.class);
        multiPostCheckMapper = mock(PersonnelMultiPostCheckMapper.class);
        snapshotMapper = mock(SafetyPermitRiskSnapshotMapper.class);
        enterpriseQualificationMapper = mock(EnterpriseQualificationMapper.class);
        ruleConfigService = mock(RuleConfigService.class);
        service = new SafetyPermitRiskServiceImpl();
        ReflectionTestUtils.setField(service, "safetyPermitMapper", safetyPermitMapper);
        ReflectionTestUtils.setField(service, "personnelCertificateMapper", personnelCertificateMapper);
        ReflectionTestUtils.setField(service, "personnelProfileMapper", personnelProfileMapper);
        ReflectionTestUtils.setField(service, "jobChangeLogMapper", jobChangeLogMapper);
        ReflectionTestUtils.setField(service, "keyPostAssignmentMapper", keyPostAssignmentMapper);
        ReflectionTestUtils.setField(service, "multiPostCheckMapper", multiPostCheckMapper);
        ReflectionTestUtils.setField(service, "snapshotMapper", snapshotMapper);
        ReflectionTestUtils.setField(service, "enterpriseQualificationMapper", enterpriseQualificationMapper);
        ReflectionTestUtils.setField(service, "ruleConfigService", ruleConfigService);
        stubConfig();
    }

    /** 规则配置打桩：全部阈值来自配置中心（测试里显式给出，正是"无硬编码"的体现）。 */
    private void stubConfig() {
        item(RuleConfigKeys.SAFETY_ABC_REQUIRED, mapOf("A", 1, "B", 1, "C", 1));
        item(RuleConfigKeys.SAFETY_ABC_ROLES, List.of("A", "B", "C"));
        item(RuleConfigKeys.SAFETY_ABC_EXEMPT_ENT_TYPES, mapOf("LABOR_SUBCONTRACT", List.of("B")));
        item(RuleConfigKeys.SAFETY_ABC_GRACE_REASON_WHITELIST, List.of("LEGAL_PERSON_CHANGE"));
        item(RuleConfigKeys.SAFETY_ABC_BLOCK_FLAGS, List.of("INVALID", "RED"));
        item(RuleConfigKeys.RISK_LEVEL_MAPPING, mapOf("NORMAL", "LOW", "YELLOW", "MEDIUM", "RED", "HIGH",
                "INVALID", "MAJOR", "FREQUENT", "MEDIUM"));
        when(ruleConfigService.getInt(RuleConfigKeys.SAFETY_ABC_FREQUENT_ALERT_THRESHOLD)).thenReturn(4);
        when(ruleConfigService.getInt(RuleConfigKeys.SAFETY_ABC_FREQUENT_WINDOW_DAYS)).thenReturn(365);
        when(ruleConfigService.getInt(RuleConfigKeys.SAFETY_ABC_INVALID_GRACE_DAYS)).thenReturn(90);
        when(ruleConfigService.getInt(RuleConfigKeys.SAFETY_ABC_GRACE_LEGAL_PERSON_CHANGE_DAYS)).thenReturn(180);
        item(RuleConfigKeys.PERSONNEL_MULTI_POST_REQUIRE_CERT, Boolean.TRUE);
        when(ruleConfigService.getBool(RuleConfigKeys.PERSONNEL_MULTI_POST_REQUIRE_CERT)).thenReturn(true);
        when(ruleConfigService.getInt(RuleConfigKeys.PROJECT_KEY_POST_CHANGE_THRESHOLD)).thenReturn(2);
        when(ruleConfigService.getInt(RuleConfigKeys.PROJECT_KEY_POST_CHANGE_WINDOW_DAYS)).thenReturn(90);
    }

    private void item(String key, Object value) {
        RuleConfigItemRespVO vo = new RuleConfigItemRespVO();
        vo.setRuleKey(key);
        vo.setValue(value);
        vo.setVersionNo(1);
        when(ruleConfigService.getItem(key)).thenReturn(vo);
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private QualificationSafetyPermitDO permit(String status) {
        QualificationSafetyPermitDO permit = new QualificationSafetyPermitDO();
        permit.setId(88L);
        permit.setEntId(1L);
        permit.setPermitNo("(陕)JZ安许证字[2023]000001");
        permit.setPermitStatus(status);
        return permit;
    }

    /** 打桩"按细分统计人数"：mapper 返回 List<Map>（sub_type / cnt），与真实 SQL 别名一致。 */
    private void stubCounts(int a, int b, int c) {
        when(personnelCertificateMapper.countUsableBySubType(eq(1L), anyString(), any(LocalDate.class)))
                .thenReturn(List.of(row("A", a), row("B", b), row("C", c)));
    }

    private Map<String, Object> row(String subType, int count) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sub_type", subType);
        map.put("cnt", count);
        return map;
    }

    private SafetyPermitRiskRespVO evaluate(String permitStatus, int a, int b, int c, boolean dryRun) {
        when(safetyPermitMapper.selectById(88L)).thenReturn(permit(permitStatus));
        stubCounts(a, b, c);
        when(snapshotMapper.countWarnings(eq(88L), any(LocalDateTime.class))).thenReturn(0L);
        when(snapshotMapper.selectFirstAllZeroAt(88L)).thenReturn(null);
        when(enterpriseQualificationMapper.selectList(any())).thenReturn(List.of());
        return service.evaluate(88L, LocalDate.now(), dryRun);
    }

    @Test
    void evaluate_red_whenAnyRoleZero() {
        SafetyPermitRiskRespVO resp = evaluate("VALID", 1, 0, 2, true);
        assertEquals(SafetyRiskFlagEnum.RED.name(), resp.getRiskFlag());
        // 阻断档由配置决定，RED 默认阻断投标
        assertTrue(resp.getBlocked());
        assertEquals("HIGH", resp.getRiskLevel());
    }

    @Test
    void evaluate_yellow_whenBelowRequired() {
        item(RuleConfigKeys.SAFETY_ABC_REQUIRED, mapOf("A", 2, "B", 1, "C", 1));
        SafetyPermitRiskRespVO resp = evaluate("VALID", 1, 1, 1, true);
        assertEquals(SafetyRiskFlagEnum.YELLOW.name(), resp.getRiskFlag());
        // 黄色仅提示不阻断：还有整改空间，直接否决过重
        assertFalse(resp.getBlocked());
    }

    @Test
    void evaluate_invalid_whenAllZero_andInvalidGraceExpired() {
        when(safetyPermitMapper.selectById(88L)).thenReturn(permit("VALID"));
        stubCounts(0, 0, 0);
        when(snapshotMapper.countWarnings(eq(88L), any(LocalDateTime.class))).thenReturn(0L);
        // 首次全 0 发生在 100 天前 > 配置的 90 天整改期 → 列失效
        when(snapshotMapper.selectFirstAllZeroAt(88L)).thenReturn(LocalDateTime.now().minusDays(100));

        SafetyPermitRiskRespVO resp = service.evaluate(88L, LocalDate.now(), true);
        assertEquals(SafetyRiskFlagEnum.INVALID.name(), resp.getRiskFlag());
        assertTrue(resp.getBlocked());
    }

    @Test
    void evaluate_red_whenAllZero_withinInvalidGraceWindow() {
        when(safetyPermitMapper.selectById(88L)).thenReturn(permit("VALID"));
        stubCounts(0, 0, 0);
        when(snapshotMapper.countWarnings(eq(88L), any(LocalDateTime.class))).thenReturn(0L);
        // 刚全 0 10 天，仍在整改宽限内 → 红色（限期整改），不能直接判失效
        when(snapshotMapper.selectFirstAllZeroAt(88L)).thenReturn(LocalDateTime.now().minusDays(10));

        SafetyPermitRiskRespVO resp = service.evaluate(88L, LocalDate.now(), true);
        assertEquals(SafetyRiskFlagEnum.RED.name(), resp.getRiskFlag());
    }

    @Test
    void evaluate_laborSubcontractExemptsRoleB() {
        when(safetyPermitMapper.selectById(88L)).thenReturn(permit("VALID"));
        stubCounts(1, 0, 2);
        when(snapshotMapper.countWarnings(eq(88L), any(LocalDateTime.class))).thenReturn(0L);
        when(snapshotMapper.selectFirstAllZeroAt(88L)).thenReturn(null);
        EnterpriseQualificationDO qual = new EnterpriseQualificationDO();
        qual.setQualCategory("施工劳务分包");
        when(enterpriseQualificationMapper.selectList(any())).thenReturn(List.of(qual));

        SafetyPermitRiskRespVO resp = service.evaluate(88L, LocalDate.now(), true);
        // 劳务分包企业 B 证为 0 既不触发红色、也不再按"低于配备"计黄（该类对其不适用）
        assertEquals(SafetyRiskFlagEnum.NORMAL.name(), resp.getRiskFlag());
        assertFalse(resp.getBlocked());
    }

    @Test
    void evaluate_graceDowngradesYellowToNormal() {
        item(RuleConfigKeys.SAFETY_ABC_REQUIRED, mapOf("A", 2, "B", 1, "C", 1));
        QualificationSafetyPermitDO permit = permit("VALID");
        permit.setGraceExpireAt(LocalDateTime.now().plusDays(30));
        permit.setGraceReason("LEGAL_PERSON_CHANGE");
        when(safetyPermitMapper.selectById(88L)).thenReturn(permit);
        stubCounts(1, 1, 1);
        when(snapshotMapper.countWarnings(eq(88L), any(LocalDateTime.class))).thenReturn(0L);
        when(snapshotMapper.selectFirstAllZeroAt(88L)).thenReturn(null);
        when(enterpriseQualificationMapper.selectList(any())).thenReturn(List.of());

        SafetyPermitRiskRespVO resp = service.evaluate(88L, LocalDate.now(), true);
        // 宽限期内降一档：YELLOW → NORMAL，不再双罚
        assertEquals(SafetyRiskFlagEnum.NORMAL.name(), resp.getRiskFlag());
        assertTrue(resp.getGraceApplied());
    }

    @Test
    void evaluate_frequent_whenWarningsReachThreshold() {
        SafetyPermitRiskRespVO resp = evaluate("VALID", 1, 1, 1, true);
        assertEquals(SafetyRiskFlagEnum.NORMAL.name(), resp.getRiskFlag());

        when(safetyPermitMapper.selectById(88L)).thenReturn(permit("VALID"));
        stubCounts(1, 1, 1);
        when(snapshotMapper.selectFirstAllZeroAt(88L)).thenReturn(null);
        when(enterpriseQualificationMapper.selectList(any())).thenReturn(List.of());
        when(snapshotMapper.countWarnings(eq(88L), any(LocalDateTime.class))).thenReturn(4L);

        SafetyPermitRiskRespVO frequent = service.evaluate(88L, LocalDate.now(), true);
        // 频繁是趋势标签：人数达标即当下合格，不阻断，但需提示监管关注
        assertEquals(SafetyRiskFlagEnum.FREQUENT.name(), frequent.getRiskFlag());
        assertFalse(frequent.getBlocked());
    }

    @Test
    void evaluate_dryRun_doesNotPersist() {
        evaluate("VALID", 1, 1, 1, true);
        // 试算不得落库：否则上线核对阶段就会产生一堆"已完成评估"的假记录
        verify(snapshotMapper, never()).insert(any(SafetyPermitRiskSnapshotDO.class));
        verify(safetyPermitMapper, never()).updateById(any(QualificationSafetyPermitDO.class));
    }

    @Test
    void evaluate_persistsSnapshotAndUpdatesPermit_whenNotDryRun() {
        evaluate("VALID", 1, 0, 2, false);
        ArgumentCaptor<SafetyPermitRiskSnapshotDO> cap = ArgumentCaptor.forClass(SafetyPermitRiskSnapshotDO.class);
        verify(snapshotMapper).insert(cap.capture());
        assertEquals(SafetyRiskFlagEnum.RED.name(), cap.getValue().getRiskFlag());
        assertNotNull(cap.getValue().getRecordHash());
        verify(safetyPermitMapper).updateById(any(QualificationSafetyPermitDO.class));
    }

    // ---------- 宽限登记 ----------

    @Test
    void registerGrace_rejectsReasonOutsideWhitelist() {
        when(safetyPermitMapper.selectById(88L)).thenReturn(permit("VALID"));
        SafetyGraceRegisterReqVO req = new SafetyGraceRegisterReqVO();
        req.setPermitId(88L);
        req.setReason("JUST_BUSY");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.registerGrace(req));
        assertEquals(ErrorCodeConstants.SAFETY_GRACE_REASON_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    void registerGrace_rejectsDaysExceedingConfiguredMax() {
        when(safetyPermitMapper.selectById(88L)).thenReturn(permit("VALID"));
        SafetyGraceRegisterReqVO req = new SafetyGraceRegisterReqVO();
        req.setPermitId(88L);
        req.setReason("LEGAL_PERSON_CHANGE");
        req.setDays(365);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.registerGrace(req));
        assertEquals(ErrorCodeConstants.SAFETY_GRACE_DAYS_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    void registerGrace_usesConfiguredDefaultDays_whenNotProvided() {
        QualificationSafetyPermitDO permit = permit("VALID");
        when(safetyPermitMapper.selectById(88L)).thenReturn(permit);
        SafetyGraceRegisterReqVO req = new SafetyGraceRegisterReqVO();
        req.setPermitId(88L);
        req.setReason("LEGAL_PERSON_CHANGE");
        req.setSourceRef("变更登记回执 2026-0001");

        service.registerGrace(req);

        ArgumentCaptor<QualificationSafetyPermitDO> cap = ArgumentCaptor.forClass(QualificationSafetyPermitDO.class);
        verify(safetyPermitMapper).updateById(cap.capture());
        // 留空取配置默认 180 天（配置驱动，非代码常量）
        assertNotNull(cap.getValue().getGraceExpireAt());
        assertTrue(cap.getValue().getGraceExpireAt().isAfter(LocalDateTime.now().plusDays(179)));
    }

    // ---------- 多岗校验 ----------

    @Test
    void checkMultiPost_fails_whenRequiredCertMissing() {
        when(personnelCertificateMapper.selectByPerson(100L)).thenReturn(List.of());
        MultiPostCheckReqVO req = new MultiPostCheckReqVO();
        req.setPersonId(100L);
        req.setPrimaryPost("PROJECT_MANAGER");
        req.setSecondaryPost("SAFETY_OFFICER");

        MultiPostCheckRespVO resp = service.checkMultiPost(req);

        assertFalse(resp.getPass());
        // 必须告诉用户"缺什么证"，只回 false 用户无从下手
        assertTrue(resp.getFailReason().contains("专职安全员"));
        verify(multiPostCheckMapper).insert(any(PersonnelMultiPostCheckDO.class));
    }

    @Test
    void checkMultiPost_passes_whenCertPresent() {
        PersonnelCertificateDO cert = new PersonnelCertificateDO();
        cert.setId(2001L);
        cert.setCertType("SAFETY_ASSESS_CERT");
        cert.setCertSubType("C");
        cert.setCertStatus("VALID");
        when(personnelCertificateMapper.selectByPerson(100L)).thenReturn(List.of(cert));
        MultiPostCheckReqVO req = new MultiPostCheckReqVO();
        req.setPersonId(100L);
        req.setPrimaryPost("PROJECT_MANAGER");
        req.setSecondaryPost("SAFETY_OFFICER");

        MultiPostCheckRespVO resp = service.checkMultiPost(req);

        assertTrue(resp.getPass());
        assertEquals(2001L, resp.getMatchedCertId());
        assertNull(resp.getFailReason());
    }

    @Test
    void checkMultiPost_rejectsSamePost() {
        MultiPostCheckReqVO req = new MultiPostCheckReqVO();
        req.setPersonId(100L);
        req.setPrimaryPost("PROJECT_MANAGER");
        req.setSecondaryPost("PROJECT_MANAGER");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.checkMultiPost(req));
        assertEquals(ErrorCodeConstants.MULTI_POST_SAME_POST.getCode(), ex.getCode());
    }

    // ---------- 关键岗位变更 ----------

    @Test
    void keyPostChanges_countsChangesExcludingInitial_andFlagsExceeded() {
        when(keyPostAssignmentMapper.selectByProjectInWindow(eq(1024L), any(LocalDate.class)))
                .thenReturn(List.of(assignment(100L, "2026-07-01"), assignment(101L, "2026-08-01"),
                        assignment(102L, "2026-09-01")));

        KeyPostChangeRespVO resp = service.getKeyPostChanges(1024L, null);

        assertEquals(1, resp.getItems().size());
        // 3 条任职记录 = 初始 1 次 + 变更 2 次；阈值 2 → 判超标
        assertEquals(2, resp.getItems().get(0).getChangeCount());
        assertTrue(resp.getItems().get(0).getExceeded());
        assertTrue(resp.getAnyExceeded());
        assertEquals("项目经理", resp.getItems().get(0).getPostLabel());
    }

    private ProjectKeyPostAssignmentDO assignment(Long personId, String startAt) {
        ProjectKeyPostAssignmentDO row = new ProjectKeyPostAssignmentDO();
        row.setProjectId(1024L);
        row.setPostCode("PROJECT_MANAGER");
        row.setPersonId(personId);
        row.setStartAt(LocalDate.parse(startAt));
        return row;
    }
}
