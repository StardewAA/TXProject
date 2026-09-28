package cn.iocoder.txgy.module.bidding.service.ruleconfig;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingRuleConfigDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingRuleConfigHistoryDO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigAuditPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigAuditRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingRuleConfigAuditDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingRuleConfigAuditMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingRuleConfigHistoryMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingRuleConfigMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规则配置服务测试（Mockito，无 DB 依赖）。
 *
 * <p>依据：用户硬约束"所有数值参数必须支持动态配置，严禁硬编码"；《电子资质七大模块技术方案.md》
 * §8.1（四级回落、value_type、legal_floor、跨键一致性）、§3.2（法定下限拦截）；契约 §1.2。
 * <p>覆盖：① 表空时回落代码兜底且标记来源；② 租户级覆盖平台默认；③ 类型/边界/一致性三道校验各自拒绝；
 * ④ 法定下限拦截（低于下限被拒并给出法规出处，等于下限放行）；⑤ **种子与兜底值一致性**（防两处默认值漂移）。
 */
class RuleConfigServiceImplTest {

    private static final Long TENANT = 1L;

    private BiddingRuleConfigMapper ruleConfigMapper;
    private BiddingRuleConfigHistoryMapper ruleConfigHistoryMapper;
    private BiddingRuleConfigAuditMapper ruleConfigAuditMapper;
    private RuleConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        ruleConfigMapper = mock(BiddingRuleConfigMapper.class);
        ruleConfigHistoryMapper = mock(BiddingRuleConfigHistoryMapper.class);
        service = new RuleConfigServiceImpl();
        ReflectionTestUtils.setField(service, "ruleConfigMapper", ruleConfigMapper);
        ReflectionTestUtils.setField(service, "ruleConfigHistoryMapper", ruleConfigHistoryMapper);
        ruleConfigAuditMapper = mock(BiddingRuleConfigAuditMapper.class);
        ReflectionTestUtils.setField(service, "ruleConfigAuditMapper", ruleConfigAuditMapper);
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 60L);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }


    // ==================== 读取审计（争议追溯，契约 §8.2） ====================

    @Test
    void resolveWithAudit_ok_writesHitSourceAndReadBy() {
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS))
                .thenReturn(row(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "120", "PUBLISHED"));

        RuleConfigItemRespVO item = service.resolveWithAudit(
                RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "SAFETY_RISK_EVAL");

        assertEquals(120, item.getValue());
        ArgumentCaptor<BiddingRuleConfigAuditDO> captor = ArgumentCaptor.forClass(BiddingRuleConfigAuditDO.class);
        verify(ruleConfigAuditMapper).insert(captor.capture());
        BiddingRuleConfigAuditDO audit = captor.getValue();
        assertEquals(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, audit.getRuleKey());
        assertEquals("TENANT", audit.getHitSource(), "命中来源必须可举证：租户覆盖要标 TENANT");
        assertEquals("SAFETY_RISK_EVAL", audit.getBizScene());
        assertEquals("SYSTEM", audit.getReadBy(), "无登录态（定时任务）读取应记为 SYSTEM，而不是留空");
        assertNotNull(audit.getReadAt());
    }

    @Test
    void resolveWithAudit_grayRow_marksHitSourceGray() {
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS))
                .thenReturn(row(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "90", "GRAY"));

        service.resolveWithAudit(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "SAFETY_RISK_EVAL");

        ArgumentCaptor<BiddingRuleConfigAuditDO> captor = ArgumentCaptor.forClass(BiddingRuleConfigAuditDO.class);
        verify(ruleConfigAuditMapper).insert(captor.capture());
        assertEquals("GRAY", captor.getValue().getHitSource(),
                "灰度生效必须与租户覆盖区分开，否则争议时无法证明当时跑的是灰度值");
    }

    @Test
    void resolveWithAudit_auditInsertFails_stillReturnsValue() {
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS))
                .thenReturn(row(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "120", "PUBLISHED"));
        doThrow(new RuntimeException("audit table unavailable")).when(ruleConfigAuditMapper)
                .insert(org.mockito.ArgumentMatchers.<BiddingRuleConfigAuditDO>any());

        RuleConfigItemRespVO item = service.resolveWithAudit(
                RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "SAFETY_RISK_EVAL");

        assertEquals(120, item.getValue(),
                "审计是旁证：写审计失败绝不能导致业务取不到规则值（取不到值=判定失败）");
    }

    @Test
    void getAuditPage_ok_mapsRowsAndFilters() {
        BiddingRuleConfigAuditDO audit = new BiddingRuleConfigAuditDO();
        audit.setId(90001L);
        audit.setRuleKey(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS);
        audit.setRuleVersion(3);
        audit.setHitSource("TENANT");
        audit.setBizScene("SAFETY_RISK_EVAL");
        audit.setReadBy("1024");
        audit.setTraceId("trace-1");
        Page<BiddingRuleConfigAuditDO> page = new Page<>(1, 10);
        page.setRecords(List.of(audit));
        page.setTotal(1L);
        when(ruleConfigAuditMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<BiddingRuleConfigAuditDO>>any(), any())).thenReturn(page);

        RuleConfigAuditPageReqVO reqVO = new RuleConfigAuditPageReqVO();
        reqVO.setRuleKey(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS);
        reqVO.setHitSource("TENANT");
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<RuleConfigAuditRespVO> result = service.getAuditPage(reqVO);

        assertEquals(1L, result.getTotal());
        assertEquals(3, result.getList().get(0).getRuleVersion().intValue());
        assertEquals("trace-1", result.getList().get(0).getTraceId());
        assertEquals("1024", result.getList().get(0).getReadBy());
    }

    private BiddingRuleConfigDO row(Long tenantId, String key, String valueType, String value, String status) {
        BiddingRuleConfigDO row = new BiddingRuleConfigDO();
        row.setId(1L);
        row.setTenantId(tenantId);
        row.setRuleKey(key);
        row.setRuleGroup("CERT");
        row.setValueType(valueType);
        row.setConfigValue(value);
        row.setStatus(status);
        row.setVersionNo(1);
        return row;
    }

    @Test
    void saveOrPublish_enforcesStoredLegalFloor_evenWhenRequestOmitsIt() {
        // 存量行带权威边界 {"min":1,"max":365}；请求**不带** legalFloor。
        // 旧实现 validateBound 见空即 return → 校验整段跳过，999 天会被写进线上（守卫形同虚设）。
        BiddingRuleConfigDO stored = row(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "60", "PUBLISHED");
        stored.setLegalFloor("{\"min\":1,\"max\":365}");
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS)).thenReturn(stored);

        RuleConfigSaveReqVO reqVO = saveReq(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "999", null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveOrPublish(reqVO));

        assertEquals(Integer.valueOf(1_060_108_002), ex.getCode(), "存量边界必须仍然生效（RULE_CONFIG_BOUND_VIOLATED）");
    }

    @Test
    void saveOrPublish_requestBound_canOnlyTighten_neverLoosen() {
        // 存量边界宽松（max=365），请求带更严的 max=100：两者都要过 → 200 应被拒
        BiddingRuleConfigDO stored = row(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "60", "PUBLISHED");
        stored.setLegalFloor("{\"min\":1,\"max\":365}");
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS)).thenReturn(stored);

        RuleConfigSaveReqVO reqVO = saveReq(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "200", "{\"min\":1,\"max\":100}");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveOrPublish(reqVO));

        assertEquals(Integer.valueOf(1_060_108_002), ex.getCode(), "请求只能让约束更严：更严的请求边界也必须生效");
    }

    @Test
    void saveOrPublish_withinStoredBound_passesFirstTwoChecks() {
        BiddingRuleConfigDO stored = row(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "60", "PUBLISHED");
        stored.setLegalFloor("{\"min\":1,\"max\":365}");
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS)).thenReturn(stored);
        when(ruleConfigMapper.insert(any(BiddingRuleConfigDO.class))).thenReturn(1);
        when(ruleConfigHistoryMapper.insert(any(BiddingRuleConfigHistoryDO.class))).thenReturn(1);

        Long id = service.saveOrPublish(saveReq(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "120", null));

        assertNotNull(id, "边界内的合法值应能保存");
    }

    /** 构造保存入参（变更原因必填，故统一给出）。 */
    private RuleConfigSaveReqVO saveReq(String ruleKey, String valueType, String value, String legalFloor) {
        RuleConfigSaveReqVO reqVO = new RuleConfigSaveReqVO();
        reqVO.setRuleKey(ruleKey);
        reqVO.setValueType(valueType);
        reqVO.setConfigValue(value);
        reqVO.setLegalFloor(legalFloor);
        reqVO.setChangeReason("单测：验证边界校验");
        return reqVO;
    }

    @Test
    void getItem_fallsBackToCodeDefault_whenTableEmpty() {
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS)).thenReturn(null);
        when(ruleConfigMapper.selectLatest(0L, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS)).thenReturn(null);

        RuleConfigItemRespVO item = service.getItem(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS);

        // 配置表未初始化也必须可用（否则整个电子证照模块起不来），但来源必须显式标记为 FALLBACK 以暴露配置缺口
        assertEquals("FALLBACK", item.getSource());
        assertEquals(60, item.getValue());
    }

    @Test
    void getItem_tenantRowOverridesPlatformDefault() {
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS))
                .thenReturn(row(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "120", "PUBLISHED"));

        RuleConfigItemRespVO item = service.getItem(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS);

        assertEquals("TENANT", item.getSource());
        assertEquals(120, item.getValue());
        assertEquals(120, service.getInt(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS));
    }

    @Test
    void getItem_ignoresUnpublishedAndOutOfWindowRows() {
        // DRAFT 不参与解析：草稿态绝不能影响线上判定
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS))
                .thenReturn(row(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "999", "DRAFT"));
        when(ruleConfigMapper.selectLatest(0L, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS)).thenReturn(null);

        assertEquals(60, service.getItem(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS).getValue());
    }

    @Test
    void getIntList_parsesThresholds() {
        when(ruleConfigMapper.selectLatest(0L, RuleConfigKeys.CERT_ALERT_THRESHOLDS))
                .thenReturn(row(0L, RuleConfigKeys.CERT_ALERT_THRESHOLDS, "JSON", "[90,60,30,7]", "PUBLISHED"));

        assertEquals(List.of(90, 60, 30, 7), service.getIntList(RuleConfigKeys.CERT_ALERT_THRESHOLDS));
    }

    @Test
    void getIntMap_parsesLegalFloors() {
        when(ruleConfigMapper.selectLatest(0L, RuleConfigKeys.CERT_ALERT_LEGAL_FLOOR_DAYS))
                .thenReturn(row(0L, RuleConfigKeys.CERT_ALERT_LEGAL_FLOOR_DAYS, "JSON",
                        "{\"SEWAGE_PERMIT\":60,\"SAFETY_PERMIT\":90}", "PUBLISHED"));

        Map<String, Integer> floors = service.getIntMap(RuleConfigKeys.CERT_ALERT_LEGAL_FLOOR_DAYS);

        assertEquals(60, floors.get("SEWAGE_PERMIT"));
        assertEquals(90, floors.get("SAFETY_PERMIT"));
    }

    // ---------- 发布校验三道关 ----------

    private RuleConfigSaveReqVO req(String key, String valueType, String value, String legalFloor) {
        RuleConfigSaveReqVO vo = new RuleConfigSaveReqVO();
        vo.setRuleKey(key);
        vo.setValueType(valueType);
        vo.setConfigValue(value);
        vo.setLegalFloor(legalFloor);
        vo.setChangeReason("单测调整");
        vo.setStatus("PUBLISHED");
        return vo;
    }

    @Test
    void save_rejectsValueTypeFormatMismatch() {
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveOrPublish(
                req(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "六十天", null)));
        // 把阈值配成非数字会导致后续大小比较静默失效，必须在发布前拦住
        assertEquals(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void save_rejectsValueOutsideLegalFloor() {
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveOrPublish(
                req(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "400", "{\"min\":1,\"max\":365}")));
        assertEquals(ErrorCodeConstants.RULE_CONFIG_BOUND_VIOLATED.getCode(), ex.getCode());
    }

    @Test
    void save_rejectsNonDescendingAlertThresholds() {
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_ALERT_THRESHOLDS)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveOrPublish(
                req(RuleConfigKeys.CERT_ALERT_THRESHOLDS, "JSON", "[30,60,90]", null)));
        // 档位必须严格递减，否则"多档取最严重"的映射会取错档
        assertEquals(ErrorCodeConstants.RULE_CONFIG_CONSISTENCY_VIOLATED.getCode(), ex.getCode());
    }

    @Test
    void save_rejectsBlankChangeReason() {
        RuleConfigSaveReqVO vo = req(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "60", null);
        vo.setChangeReason("  ");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveOrPublish(vo));
        assertEquals(ErrorCodeConstants.RULE_CONFIG_CHANGE_REASON_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void save_rejectsIllegalStatusTransition_fromRolledBack() {
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS))
                .thenReturn(row(TENANT, RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "60", "ROLLED_BACK"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveOrPublish(
                req(RuleConfigKeys.CERT_EXPIRING_WINDOW_DAYS, "INT", "90", null)));
        assertEquals(ErrorCodeConstants.RULE_CONFIG_STATUS_INVALID.getCode(), ex.getCode());
    }

    // ---------- 法定下限拦截（方案 §3） ----------

    private void stubFloorRow() {
        when(ruleConfigMapper.selectLatest(0L, RuleConfigKeys.CERT_ALERT_LEGAL_FLOOR_DAYS))
                .thenReturn(row(0L, RuleConfigKeys.CERT_ALERT_LEGAL_FLOOR_DAYS, "JSON",
                        "{\"SEWAGE_PERMIT\":60}", "PUBLISHED"));
        when(ruleConfigMapper.selectLatest(TENANT, RuleConfigKeys.CERT_ALERT_LEGAL_FLOOR_DAYS)).thenReturn(null);
    }

    @Test
    void validateAlertThresholds_blocksBelowLegalFloor_withRegulationRef() {
        stubFloorRow();

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateAlertThresholds("SEWAGE_PERMIT", List.of(30, 7)));
        // 排污许可延续须提前 60 日：最大档小于 60 天就会错过法定窗口，必须阻断
        assertEquals(ErrorCodeConstants.LEGAL_FLOOR_VIOLATED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("60"));
    }

    @Test
    void validateAlertThresholds_allowsExactlyAtFloor() {
        stubFloorRow();
        // 等于下限属合法配置（不得"多拦一档"造成业务无法按法定最低要求配置）
        service.validateAlertThresholds("SEWAGE_PERMIT", List.of(60, 30, 7));
    }

    @Test
    void validateAlertThresholds_allowsUnknownCategory() {
        stubFloorRow();
        // 未登记下限的类别不做限制（法规未覆盖的证照不应被系统凭空限制）
        service.validateAlertThresholds("UNKNOWN_CATEGORY", List.of(7));
    }

    // ---------- 种子与兜底一致性（防默认值漂移） ----------

    @Test
    void sqlSeed_matchesCodeFallbacks() throws Exception {
        // 规范出处：本次 SQL 收敛（电子资质域 16 个增量脚本合并进 master-credential-sync.sql，原文件归档 sql/history/）。
        // 为什么这样写：种子一致性门禁不能因为"文件搬家"失效，故按 归档脚本 → 合并包 → 旧路径 顺序解析；
        //   合并包包含同一批种子文本，指向它可额外保证"种子确实进了新环境一键包"。
        // 业务目标：无论脚本怎么整理，代码兜底默认值与 SQL 种子必须始终一致，门禁始终有效。
        // 2026-09-13 加固（原实现有两处会让门禁假通过/漏检）：
        //   ① 原来"取第一个存在的路径就收工"（归档脚本优先）→ 注释声称的"顺带保证种子进了合并包"其实**从未生效**；
        //      本会话就真的发生过"新种子只在归档脚本、合并包漏了"（由 sql/tools/check-master-mirror.js 发现）。
        //      现改为**对每个存在的候选文件都校验**，归档脚本与合并包各自都要有全部种子。
        //   ② 原来的断言是整文件子串检查 → 键名只要在**注释里**出现过就能通过（弱断言）。
        //      现改为必须命中"真实的种子行"（(id, 0, 'key', ...) 形状），且兜底值必须落在**同一行**内。
        List<Path> seedFiles = new ArrayList<>();
        for (Path candidate : new Path[]{
                Path.of("..", "sql", "history", "2026-09-16-rule-config-center.sql"),
                Path.of("..", "sql", "master-credential-sync.sql"),
                Path.of("..", "sql", "2026-09-16-rule-config-center.sql")}) {
            if (Files.exists(candidate)) {
                seedFiles.add(candidate);
            }
        }
        assertFalse(seedFiles.isEmpty(), "未找到规则配置 DDL/种子脚本（已尝试 sql/history/ 与 sql/master-credential-sync.sql）");
        assertFalse(RuleConfigKeys.allFallbacks().isEmpty(), "兜底键不得为空");
        for (Path sql : seedFiles) {
            String text = Files.readString(sql);
            // 去掉引号后比对，规避 SQL 单引号与 JSON 双引号的转义差异
            String normalizedSql = text.replace("\"", "");

            for (Map.Entry<String, String> entry : RuleConfigKeys.allFallbacks().entrySet()) {
                Matcher row = Pattern.compile("\\(\\s*\\d+\\s*,\\s*0\\s*,\\s*'"
                                + Pattern.quote(entry.getKey()) + "'[^;]*?\\)", Pattern.DOTALL)
                        .matcher(text);
                assertTrue(row.find(), "规则键在 " + sql.getFileName() + " 中没有真实种子行：" + entry.getKey());
                assertTrue(row.group().replace("\"", "").contains(entry.getValue().replace("\"", "")),
                        "兜底值与种子值不一致（配置中心最常见的腐坏方式）：" + entry.getKey());
            }
        }
    }
}
