package cn.iocoder.txgy.module.bidding.service.safety;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.KeyPostAssignmentCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.KeyPostAssignmentRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.KeyPostChangeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyGraceRegisterReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyPermitRiskRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
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
import cn.iocoder.txgy.module.bidding.service.project.ProjectService;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
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
import org.mockito.ArgumentMatchers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private ProjectService projectService;
    private SafetyRiskItemService safetyRiskItemService;
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
        projectService = mock(ProjectService.class);
        ReflectionTestUtils.setField(service, "projectService", projectService);
        safetyRiskItemService = mock(SafetyRiskItemService.class);
        ReflectionTestUtils.setField(service, "safetyRiskItemService", safetyRiskItemService);
        // 关键岗位写入口必须校验项目存在（追溯链完整性），因此给默认桩；不桩会让"项目不存在"的
        // 用例因为 NPE 而"看起来通过"，掩盖真正的校验缺失。
        ProjectRespVO project = new ProjectRespVO();
        project.setId(77L);
        project.setProjectCode("P-2026-077");
        when(projectService.getProject(any())).thenReturn(project);
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
        item(RuleConfigKeys.SAFETY_ABC_FREQUENT_ALERT_THRESHOLD, 4);
        item(RuleConfigKeys.SAFETY_ABC_FREQUENT_WINDOW_DAYS, 365);
        item(RuleConfigKeys.SAFETY_ABC_INVALID_GRACE_DAYS, 90);
        item(RuleConfigKeys.SAFETY_ABC_GRACE_LEGAL_PERSON_CHANGE_DAYS, 180);
        item(RuleConfigKeys.PERSONNEL_MULTI_POST_REQUIRE_CERT, Boolean.TRUE);
        item(RuleConfigKeys.PERSONNEL_MULTI_POST_CERT_SUBTYPE_MAPPING,
                mapOf("DEFAULT", "C", "SAFETY_OFFICER", "C", "PROJECT_MANAGER", "C"));
        item(RuleConfigKeys.PROJECT_KEY_POST_CHANGE_THRESHOLD, 2);
        item(RuleConfigKeys.PROJECT_KEY_POST_CHANGE_WINDOW_DAYS, 90);
    }

    /**
     * 打桩：风险判定点读取规则走 {@code resolveWithAudit}（读值同时写读取审计）。
     *
     * <p>为什么桩的是 resolveWithAudit 而不是 getItem/getInt：判定点已统一改为带审计的读取入口
     * （《电子资质七大模块技术方案.md》§8.2），若仍桩旧方法，测试会在生产代码真的漏写审计时"照绿"。
     */
    private void item(String key, Object value) {
        RuleConfigItemRespVO vo = new RuleConfigItemRespVO();
        vo.setRuleKey(key);
        vo.setValue(value);
        vo.setVersionNo(1);
        when(ruleConfigService.resolveWithAudit(eq(key), anyString())).thenReturn(vo);
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
    void checkMultiPost_requiredSubType_comesFromConfigMatrix_notFromCode() {
        // 业务确认"兼任项目经理要 B 类"后，运营只改配置：这里把矩阵改成 B 类，校验必须立刻跟着变
        item(RuleConfigKeys.PERSONNEL_MULTI_POST_CERT_SUBTYPE_MAPPING,
                mapOf("DEFAULT", "C", "PROJECT_MANAGER", "B"));
        PersonnelCertificateDO cCert = new PersonnelCertificateDO();
        cCert.setId(2001L);
        cCert.setCertType("SAFETY_ASSESS_CERT");
        cCert.setCertSubType("C");
        cCert.setCertStatus("VALID");
        when(personnelCertificateMapper.selectByPerson(100L)).thenReturn(List.of(cCert));
        MultiPostCheckReqVO req = new MultiPostCheckReqVO();
        req.setPersonId(100L);
        req.setPrimaryPost("SAFETY_OFFICER");
        req.setSecondaryPost("PROJECT_MANAGER");

        MultiPostCheckRespVO resp = service.checkMultiPost(req);

        assertEquals("B", resp.getRequiredCertSubType(), "需要的细分必须来自配置矩阵（原实现两分支同值写死 C）");
        assertFalse(resp.getPass(), "只有 C 类证时不应通过（矩阵已把项目经理改成 B 类）");
    }

    @Test
    void checkMultiPost_mappingWithoutDefault_failsLoudly() {
        // 矩阵既没有该岗位、也没有 DEFAULT：宁可报错，也不要静默用代码兜底值放过一次配证校验
        item(RuleConfigKeys.PERSONNEL_MULTI_POST_CERT_SUBTYPE_MAPPING, mapOf("PROJECT_MANAGER", "C"));
        MultiPostCheckReqVO req = new MultiPostCheckReqVO();
        req.setPersonId(100L);
        req.setPrimaryPost("PROJECT_MANAGER");
        req.setSecondaryPost("SAFETY_OFFICER");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.checkMultiPost(req));
        assertEquals(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID.getCode(), ex.getCode());
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

    // ========== 关键岗位任职/变更登记（唯一写入口） ==========

    @Test
    void assignKeyPost_firstAssignment_isSeqOneWithoutPreviousHolder() {
        when(keyPostAssignmentMapper.selectCurrent(77L, "PROJECT_MANAGER")).thenReturn(null);
        when(keyPostAssignmentMapper.selectMaxSeq(77L, "PROJECT_MANAGER")).thenReturn(0);
        when(personnelProfileMapper.selectById(2048L)).thenReturn(new PersonnelProfileDO());
        when(keyPostAssignmentMapper.selectByProjectInWindow(eq(77L), any())).thenReturn(List.of(assignment(1, 2048L)));

        KeyPostAssignmentRespVO resp = service.assignKeyPost(
                req("PROJECT_MANAGER", 2048L, LocalDate.of(2026, 9, 13)));

        assertEquals(1, resp.getChangeSeq());
        assertEquals("项目经理", resp.getPostLabel());
        assertNull(resp.getPreviousPersonId(), "首次任职没有被结束任期的上任");
        assertNull(resp.getPreviousEndAt());
        assertEquals(0, resp.getWindowChangeCount(), "首条是初始任职，不算变更");
        assertEquals(2, resp.getThreshold(), "阈值来自配置中心（project.key.post.change.threshold）");
        assertEquals(90, resp.getWindowDays(), "窗口来自配置中心（project.key.post.change.window.days）");
        assertFalse(resp.getExceeded());
        ArgumentCaptor<ProjectKeyPostAssignmentDO> captor = ArgumentCaptor.forClass(ProjectKeyPostAssignmentDO.class);
        verify(keyPostAssignmentMapper).insert(captor.capture());
        assertEquals("P-2026-077", captor.getValue().getTenderProjectCode(), "招标项目编号从项目主档带出（追溯用）");
        assertNull(captor.getValue().getEndAt(), "在任记录的 end_at 必须为空");
    }

    @Test
    void assignKeyPost_change_closesPreviousTenureAndBumpsSeq() {
        ProjectKeyPostAssignmentDO current = assignment(1, 1024L);
        current.setId(500L);
        current.setStartAt(LocalDate.of(2026, 1, 1));
        when(keyPostAssignmentMapper.selectCurrent(77L, "PROJECT_MANAGER")).thenReturn(current);
        when(keyPostAssignmentMapper.selectMaxSeq(77L, "PROJECT_MANAGER")).thenReturn(1);
        when(keyPostAssignmentMapper.updateById(current)).thenReturn(1);
        when(personnelProfileMapper.selectById(2048L)).thenReturn(new PersonnelProfileDO());
        when(keyPostAssignmentMapper.selectByProjectInWindow(eq(77L), any()))
                .thenReturn(List.of(current, assignment(2, 2048L)));

        LocalDate startAt = LocalDate.of(2026, 6, 1);
        KeyPostAssignmentRespVO resp = service.assignKeyPost(req("PROJECT_MANAGER", 2048L, startAt));

        assertEquals(2, resp.getChangeSeq());
        assertEquals(1024L, resp.getPreviousPersonId());
        assertEquals(startAt, resp.getPreviousEndAt());
        assertEquals(startAt, current.getEndAt(), "上任任期由服务端闭合为本次开始日（一次登记、不留中间态）");
        assertEquals(1, resp.getWindowChangeCount(), "窗口内 2 条记录 - 1 = 1 次变更");
        assertFalse(resp.getExceeded());
    }

    @Test
    void assignKeyPost_windowCountReachesThreshold_flagsExceeded() {
        ProjectKeyPostAssignmentDO current = assignment(2, 1024L);
        current.setStartAt(LocalDate.of(2026, 3, 1));
        when(keyPostAssignmentMapper.selectCurrent(77L, "PROJECT_MANAGER")).thenReturn(current);
        when(keyPostAssignmentMapper.selectMaxSeq(77L, "PROJECT_MANAGER")).thenReturn(2);
        when(keyPostAssignmentMapper.updateById(current)).thenReturn(1);
        when(personnelProfileMapper.selectById(2048L)).thenReturn(new PersonnelProfileDO());
        when(keyPostAssignmentMapper.selectByProjectInWindow(eq(77L), any()))
                .thenReturn(List.of(assignment(1, 9L), current, assignment(3, 2048L)));

        KeyPostAssignmentRespVO resp = service.assignKeyPost(
                req("PROJECT_MANAGER", 2048L, LocalDate.of(2026, 6, 1)));

        assertEquals(2, resp.getWindowChangeCount());
        assertEquals(2, resp.getThreshold());
        assertTrue(resp.getExceeded(), "达阈值必须当场提示（挂靠嫌疑），前端据此在同一屏高亮");
    }

    @Test
    void assignKeyPost_otherPostInSameProject_notCounted() {
        when(keyPostAssignmentMapper.selectCurrent(77L, "SAFETY_OFFICER")).thenReturn(null);
        when(keyPostAssignmentMapper.selectMaxSeq(77L, "SAFETY_OFFICER")).thenReturn(0);
        when(personnelProfileMapper.selectById(2048L)).thenReturn(new PersonnelProfileDO());
        // 窗口内有其它岗位的多次变更：统计必须按岗位分别算，否则一个岗位换人会把别的岗位也带成"超阈值"
        ProjectKeyPostAssignmentDO otherPost = assignment(1, 9L);
        otherPost.setPostCode("PROJECT_MANAGER");
        when(keyPostAssignmentMapper.selectByProjectInWindow(eq(77L), any()))
                .thenReturn(List.of(otherPost, otherPost, otherPost, assignment(1, 2048L)));

        KeyPostAssignmentRespVO resp = service.assignKeyPost(
                req("SAFETY_OFFICER", 2048L, LocalDate.of(2026, 6, 1)));

        assertEquals(0, resp.getWindowChangeCount());
        assertFalse(resp.getExceeded());
    }

    @Test
    void assignKeyPost_invalidPostCode_rejectedBeforeAnyWrite() {
        KeyPostAssignmentCreateReqVO req = req("PROJECT_OWNER", 2048L, LocalDate.of(2026, 6, 1));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.assignKeyPost(req));
        assertEquals(ErrorCodeConstants.KEY_POST_INVALID.getCode(), ex.getCode(),
                "岗位非法复用既有多岗校验错误码（1_060_107_028）：一个条件只应有一个码");
        verify(keyPostAssignmentMapper, never()).insert(ArgumentMatchers.<ProjectKeyPostAssignmentDO>any());
        verify(projectService, never()).getProject(any());
    }

    @Test
    void assignKeyPost_startBeforeCurrentTenure_rejected() {
        ProjectKeyPostAssignmentDO current = assignment(1, 1024L);
        current.setStartAt(LocalDate.of(2026, 6, 1));
        when(keyPostAssignmentMapper.selectCurrent(77L, "PROJECT_MANAGER")).thenReturn(current);
        // 人员存在性在实现里先于任期校验（先校验请求本身、再校验状态），故此处也要桩；
        // 不桩会让断言命中 PERSONNEL_NOT_EXISTS 而"看起来是校验生效"——桩不全等于没测到目标分支。
        when(personnelProfileMapper.selectById(2048L)).thenReturn(new PersonnelProfileDO());

        KeyPostAssignmentCreateReqVO req = req("PROJECT_MANAGER", 2048L, LocalDate.of(2026, 5, 1));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.assignKeyPost(req));
        assertEquals(ErrorCodeConstants.PROJECT_KEY_POST_CHANGE_DATE_INVALID.getCode(), ex.getCode());
        verify(keyPostAssignmentMapper, never()).insert(ArgumentMatchers.<ProjectKeyPostAssignmentDO>any());
        assertNull(current.getEndAt(), "校验失败不得改动在任记录");
    }

    @Test
    void assignKeyPost_samePersonInPost_rejectedToProtectStatistics() {
        ProjectKeyPostAssignmentDO current = assignment(1, 2048L);
        current.setStartAt(LocalDate.of(2026, 1, 1));
        when(keyPostAssignmentMapper.selectCurrent(77L, "PROJECT_MANAGER")).thenReturn(current);
        when(personnelProfileMapper.selectById(2048L)).thenReturn(new PersonnelProfileDO());

        KeyPostAssignmentCreateReqVO req = req("PROJECT_MANAGER", 2048L, LocalDate.of(2026, 6, 1));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.assignKeyPost(req));
        assertEquals(ErrorCodeConstants.PROJECT_KEY_POST_SAME_PERSON.getCode(), ex.getCode());
        verify(keyPostAssignmentMapper, never()).insert(ArgumentMatchers.<ProjectKeyPostAssignmentDO>any());
    }

    @Test
    void assignKeyPost_personNotExists_rejected() {
        when(personnelProfileMapper.selectById(2048L)).thenReturn(null);

        KeyPostAssignmentCreateReqVO req = req("PROJECT_MANAGER", 2048L, LocalDate.of(2026, 6, 1));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.assignKeyPost(req));
        assertEquals(ErrorCodeConstants.PERSONNEL_NOT_EXISTS.getCode(), ex.getCode());
        verify(keyPostAssignmentMapper, never()).insert(ArgumentMatchers.<ProjectKeyPostAssignmentDO>any());
    }

    @Test
    void assignKeyPost_seqConflictFromUniqueKey_mappedToReadableError() {
        when(keyPostAssignmentMapper.selectCurrent(77L, "PROJECT_MANAGER")).thenReturn(null);
        when(keyPostAssignmentMapper.selectMaxSeq(77L, "PROJECT_MANAGER")).thenReturn(1);
        when(personnelProfileMapper.selectById(2048L)).thenReturn(new PersonnelProfileDO());
        // 模拟并发：另一请求先插入同一 (项目,岗位,序号)，数据库唯一键 uk_post_seq 抛冲突
        doThrow(new DuplicateKeyException("uk_post_seq")).when(keyPostAssignmentMapper)
                .insert(ArgumentMatchers.<ProjectKeyPostAssignmentDO>any());

        KeyPostAssignmentCreateReqVO req = req("PROJECT_MANAGER", 2048L, LocalDate.of(2026, 6, 1));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.assignKeyPost(req));
        assertEquals(ErrorCodeConstants.PROJECT_KEY_POST_SEQ_CONFLICT.getCode(), ex.getCode(),
                "唯一键冲突必须转成可读错误（前端可重试），而不是 500");
    }

    @Test
    void assignKeyPost_previousRowChangedConcurrently_rejected() {
        ProjectKeyPostAssignmentDO current = assignment(1, 1024L);
        current.setStartAt(LocalDate.of(2026, 1, 1));
        when(keyPostAssignmentMapper.selectCurrent(77L, "PROJECT_MANAGER")).thenReturn(current);
        when(personnelProfileMapper.selectById(2048L)).thenReturn(new PersonnelProfileDO());
        when(keyPostAssignmentMapper.updateById(current)).thenReturn(0);

        KeyPostAssignmentCreateReqVO req = req("PROJECT_MANAGER", 2048L, LocalDate.of(2026, 6, 1));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.assignKeyPost(req));
        assertEquals(ErrorCodeConstants.PROJECT_KEY_POST_CONCURRENT_MODIFIED.getCode(), ex.getCode());
        verify(keyPostAssignmentMapper, never()).insert(ArgumentMatchers.<ProjectKeyPostAssignmentDO>any());
    }

    private KeyPostAssignmentCreateReqVO req(String postCode, Long personId, LocalDate startAt) {
        KeyPostAssignmentCreateReqVO req = new KeyPostAssignmentCreateReqVO();
        req.setProjectId(77L);
        req.setPostCode(postCode);
        req.setPersonId(personId);
        req.setStartAt(startAt);
        req.setChangeReason("原任职人员离职，由持证人员接任");
        return req;
    }

    private ProjectKeyPostAssignmentDO assignment(int seq, Long personId) {
        ProjectKeyPostAssignmentDO row = new ProjectKeyPostAssignmentDO();
        row.setProjectId(77L);
        row.setTenderProjectCode("P-2026-077");
        row.setPostCode("PROJECT_MANAGER");
        row.setPersonId(personId);
        row.setStartAt(LocalDate.of(2026, 1, 1).plusDays(seq));
        row.setChangeSeq(seq);
        return row;
    }
}
