package cn.iocoder.txgy.module.bidding.service.ruleconfig;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingRuleConfigDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingRuleConfigHistoryMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingRuleConfigMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private RuleConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        ruleConfigMapper = mock(BiddingRuleConfigMapper.class);
        ruleConfigHistoryMapper = mock(BiddingRuleConfigHistoryMapper.class);
        service = new RuleConfigServiceImpl();
        ReflectionTestUtils.setField(service, "ruleConfigMapper", ruleConfigMapper);
        ReflectionTestUtils.setField(service, "ruleConfigHistoryMapper", ruleConfigHistoryMapper);
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 60L);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
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
        Path sql = Path.of("..", "sql", "2026-09-16-rule-config-center.sql");
        assertTrue(Files.exists(sql), "未找到规则配置 DDL/种子脚本，无法校验默认值一致性：" + sql.toAbsolutePath());
        String text = Files.readString(sql);
        // 去掉引号后比对，规避 SQL 单引号与 JSON 双引号的转义差异
        String normalizedSql = text.replace("\"", "");

        assertFalse(RuleConfigKeys.allFallbacks().isEmpty(), "兜底键不得为空");
        for (Map.Entry<String, String> entry : RuleConfigKeys.allFallbacks().entrySet()) {
            assertTrue(text.contains("'" + entry.getKey() + "'"),
                    "规则键未在种子脚本中登记：" + entry.getKey());
            assertTrue(normalizedSql.contains(entry.getValue().replace("\"", "")),
                    "兜底值与种子值不一致（配置中心最常见的腐坏方式）：" + entry.getKey());
        }
    }
}
