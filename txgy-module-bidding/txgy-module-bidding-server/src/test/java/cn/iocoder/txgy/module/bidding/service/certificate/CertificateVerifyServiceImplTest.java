package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyHistoryPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyHistoryRespVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.mockito.ArgumentMatchers;
import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertReqHeadVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertRespHeadVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileRespVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertChangeLogDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertVerifyHistoryDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertFileVersionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertVerifyHistoryMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertVerifyResultEnum;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateVerificationStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证照验真服务规则测试（Mockito，TDD）：
 * 回执映射 0→VERIFIED/1→INVALID/4→UNAVAILABLE（状态保持）、幂等同分钟返回既有、VERIFYING 互斥、
 * 历史留痕与 verification_status 哈希链审计、渠道非法拒绝。
 * 依据：《电子证件全流程管理_数据库设计.md》v3.3 §17.5、GB/T 36906—2018 §6.4。
 */
class CertificateVerifyServiceImplTest {

    private CertificateInfoMapper certificateInfoMapper;
    private CertVerifyHistoryMapper certVerifyHistoryMapper;
    private CertChangeLogMapper certChangeLogMapper;
    private CertVerifyGateway certVerifyGateway;
    private CertFileVersionMapper certFileVersionMapper;
    private RuleConfigService ruleConfigService;
    private CertificateVerifyServiceImpl service;

    @BeforeEach
    void setUp() {
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        certVerifyHistoryMapper = mock(CertVerifyHistoryMapper.class);
        certChangeLogMapper = mock(CertChangeLogMapper.class);
        certVerifyGateway = mock(CertVerifyGateway.class);
        certFileVersionMapper = mock(CertFileVersionMapper.class);
        service = new CertificateVerifyServiceImpl();
        ReflectionTestUtils.setField(service, "certificateInfoMapper", certificateInfoMapper);
        ReflectionTestUtils.setField(service, "certVerifyHistoryMapper", certVerifyHistoryMapper);
        ReflectionTestUtils.setField(service, "certChangeLogMapper", certChangeLogMapper);
        // 幂等窗口来自规则配置中心（cert.verify.idempotent.window.seconds）；
        // 不桩会让读取抛 RULE_CONFIG_VALUE_FORMAT_INVALID —— 那正是"配置缺失不静默兜底"的设计。
        ruleConfigService = mock(RuleConfigService.class);
        ReflectionTestUtils.setField(service, "ruleConfigService", ruleConfigService);
        stubVerifyWindow(60);
        ReflectionTestUtils.setField(service, "certVerifyGateway", certVerifyGateway);
        ReflectionTestUtils.setField(service, "certFileVersionMapper", certFileVersionMapper);
    }

    @Test
    void verify_resultZero_marksVerifiedAndLogsAudit() {
        CertificateInfoDO cert = certOf("UNVERIFIED");
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert);
        when(certVerifyHistoryMapper.selectByIdempotentKey(any(String.class))).thenReturn(null);
        when(certVerifyGateway.verifyInfo(any(EccertVerifyInfoReqVO.class))).thenReturn(
                new EccertVerifyInfoRespVO(new EccertRespHeadVO("0", "调用成功"),
                        new EccertVerifyInfoRespVO.Data("0", "0 信息正确且有效")));
        when(certVerifyHistoryMapper.insert(any(CertVerifyHistoryDO.class))).thenAnswer(inv -> {
            CertVerifyHistoryDO h = inv.getArgument(0);
            h.setId(77L);
            return 1;
        });
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(1);
        when(certChangeLogMapper.selectLastRecordHash(1L)).thenReturn(null);

        Long id = service.verifyCertificate(1L, "FOUR_PLATFORM", 42L);

        assertEquals(77L, id);
        assertEquals(CertificateVerificationStatusEnum.VERIFIED.name(), cert.getVerificationStatus());
        assertEquals(CertVerifyResultEnum.MATCH.name(), historyOf().getVerifyResult());
        assertTrue(historyOf().getResultSnapshot().contains("\"0 信息正确且有效\"")
                || historyOf().getResultSnapshot().contains("0 信息正确且有效"));
        // 审计行：verification_status UNVERIFIED→VERIFIED，API_SYNC，批量 VERIFY:77
        ArgumentCaptor<CertChangeLogDO> logs = ArgumentCaptor.forClass(CertChangeLogDO.class);
        verify(certChangeLogMapper).insert(logs.capture());
        assertEquals("verification_status", logs.getValue().getFieldName());
        assertEquals("VERIFY:77", logs.getValue().getBatchId());
        assertEquals(CertificateChangeTypeEnum.UPDATE.name(), logs.getValue().getChangeType());
        assertEquals(64, logs.getValue().getRecordHash().length());
        assertEquals("ROOT", logs.getValue().getPrevHash());
    }

    @Test
    void verify_result1to3_marksInvalid() {
        CertificateInfoDO cert = certOf("VERIFIED");
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert);
        when(certVerifyHistoryMapper.selectByIdempotentKey(any(String.class))).thenReturn(null);
        when(certVerifyGateway.verifyInfo(any(EccertVerifyInfoReqVO.class))).thenReturn(
                new EccertVerifyInfoRespVO(new EccertRespHeadVO("0", "调用成功"),
                        new EccertVerifyInfoRespVO.Data("2", "2 信息正确且证照暂时失效")));
        when(certVerifyHistoryMapper.insert(any(CertVerifyHistoryDO.class))).thenAnswer(inv -> {
            CertVerifyHistoryDO h = inv.getArgument(0);
            h.setId(78L);
            return 1;
        });
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(1);

        service.verifyCertificate(1L, "FOUR_PLATFORM", 42L);

        assertEquals(CertificateVerificationStatusEnum.INVALID.name(), cert.getVerificationStatus());
        assertEquals(CertVerifyResultEnum.MISMATCH.name(), historyOf().getVerifyResult());
    }

    @Test
    void verify_result4or5_unavailableKeepsUnverified() {
        CertificateInfoDO cert = certOf("UNVERIFIED");
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert);
        when(certVerifyHistoryMapper.selectByIdempotentKey(any(String.class))).thenReturn(null);
        when(certVerifyGateway.verifyInfo(any(EccertVerifyInfoReqVO.class))).thenReturn(
                new EccertVerifyInfoRespVO(new EccertRespHeadVO("0", "调用成功"),
                        new EccertVerifyInfoRespVO.Data("5", "5 查无此信息")));
        when(certVerifyHistoryMapper.insert(any(CertVerifyHistoryDO.class))).thenAnswer(inv -> {
            CertVerifyHistoryDO h = inv.getArgument(0);
            h.setId(79L);
            return 1;
        });
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(1);

        service.verifyCertificate(1L, "FOUR_PLATFORM", 42L);

        assertEquals(CertVerifyResultEnum.UNAVAILABLE.name(), historyOf().getVerifyResult());
        assertEquals(CertificateVerificationStatusEnum.UNVERIFIED.name(), cert.getVerificationStatus(),
                "UNAVAILABLE 不轻易判假，台账保持 UNVERIFIED");
        assertEquals("FOUR_PLATFORM", cert.getVerifyApiSource());
    }

    @Test
    void verify_withinIdempotentWindow_reusesHistoryWithoutGatewayCall() {
        CertificateInfoDO cert = certOf("UNVERIFIED");
        CertVerifyHistoryDO existing = new CertVerifyHistoryDO();
        existing.setId(5L);
        existing.setVerifiedAt(LocalDateTime.now().minusSeconds(30));
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert);
        when(certVerifyHistoryMapper.selectLatestWithinWindow(any(String.class), any(LocalDateTime.class)))
                .thenReturn(existing);

        Long id = service.verifyCertificate(1L, "FOUR_PLATFORM", 42L);

        assertEquals(5L, id, "窗口内重试应幂等返回既有历史");
        verify(certVerifyGateway, never()).verifyInfo(any());
        // 前缀必须是 INFO:（种类在键首）：验信息与验文件渠道相同，只有前缀能防止两类结论互相复用
        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
        verify(certVerifyHistoryMapper).selectLatestWithinWindow(prefix.capture(), any(LocalDateTime.class));
        assertTrue(prefix.getValue().startsWith("INFO:1:FOUR_PLATFORM:"),
                "实际前缀=" + prefix.getValue());
    }

    @Test
    void verify_windowStart_comesFromConfigCenter() {
        // 窗口 300 秒：传给查询的下界应是 now - 300s（±5 秒容差），证明窗口真的来自配置而不是写死的 60/分钟
        stubVerifyWindow(300);
        when(certificateInfoMapper.selectById(1L)).thenReturn(certOf("UNVERIFIED"));
        when(certVerifyHistoryMapper.selectLatestWithinWindow(any(String.class), any(LocalDateTime.class)))
                .thenReturn(null);
        when(certVerifyGateway.verifyInfo(any(EccertVerifyInfoReqVO.class))).thenReturn(verifyResp("0"));
        stubPersistPath();

        service.verifyCertificate(1L, "FOUR_PLATFORM", 42L);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(certVerifyHistoryMapper).selectLatestWithinWindow(any(String.class), since.capture());
        long gap = ChronoUnit.SECONDS.between(since.getValue(), LocalDateTime.now());
        assertTrue(gap >= 295 && gap <= 305, "窗口下界应约为 now-300s，实际差 " + gap + " 秒");
    }

    @Test
    void verify_outsideWindow_callsGatewayAndPersists() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(certOf("UNVERIFIED"));
        when(certVerifyHistoryMapper.selectLatestWithinWindow(any(String.class), any(LocalDateTime.class)))
                .thenReturn(null);
        when(certVerifyGateway.verifyInfo(any(EccertVerifyInfoReqVO.class))).thenReturn(verifyResp("0"));
        stubPersistPath();

        service.verifyCertificate(1L, "FOUR_PLATFORM", 42L);

        verify(certVerifyGateway).verifyInfo(any());
        ArgumentCaptor<CertVerifyHistoryDO> history = ArgumentCaptor.forClass(CertVerifyHistoryDO.class);
        verify(certVerifyHistoryMapper).insert(history.capture());
        assertTrue(history.getValue().getIdempotentKey().startsWith("INFO:1:FOUR_PLATFORM:"),
                "兜底键同样带种类前缀，实际=" + history.getValue().getIdempotentKey());
    }

    @Test
    void verify_concurrentDuplicateKey_returnsExistingRow() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(certOf("UNVERIFIED"));
        when(certVerifyHistoryMapper.selectLatestWithinWindow(any(String.class), any(LocalDateTime.class)))
                .thenReturn(null);
        when(certVerifyGateway.verifyInfo(any(EccertVerifyInfoReqVO.class))).thenReturn(verifyResp("0"));
        doThrow(new DuplicateKeyException("uk_verify_idem")).when(certVerifyHistoryMapper)
                .insert(ArgumentMatchers.<CertVerifyHistoryDO>any());
        CertVerifyHistoryDO dup = new CertVerifyHistoryDO();
        dup.setId(9L);
        when(certVerifyHistoryMapper.selectByIdempotentKey(any(String.class))).thenReturn(dup);

        Long id = service.verifyCertificate(1L, "FOUR_PLATFORM", 42L);

        assertEquals(9L, id, "并发同桶冲突时以既有记录为准（唯一键兜底），不抛 500");
    }

    @Test
    void verifyFile_usesFilePrefix_notSharedWithInfo() {
        CertFileVersionDO file = new CertFileVersionDO();
        file.setId(7L);
        file.setFileHash("abc123");
        when(certificateInfoMapper.selectById(1L)).thenReturn(certOf("UNVERIFIED"));
        when(certFileVersionMapper.selectAnyCurrentByCertId(1L)).thenReturn(file);
        when(certVerifyHistoryMapper.selectLatestWithinWindow(any(String.class), any(LocalDateTime.class)))
                .thenReturn(null);

        assertThrows(Exception.class, () -> service.verifyCertificateFile(1L, "FOUR_PLATFORM", 42L));

        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
        verify(certVerifyHistoryMapper).selectLatestWithinWindow(prefix.capture(), any(LocalDateTime.class));
        assertTrue(prefix.getValue().startsWith("FILE:1:FOUR_PLATFORM:"),
                "验文件必须用 FILE: 前缀（与 INFO: 不互相包含），实际=" + prefix.getValue());
    }

    /** 打桩"落库 + 回写台账"这条成功路径（与其它成功用例同一口径，避免用例只测到一半）。 */
    private void stubPersistPath() {
        when(certVerifyHistoryMapper.insert(any(CertVerifyHistoryDO.class))).thenAnswer(inv -> {
            CertVerifyHistoryDO h = inv.getArgument(0);
            h.setId(77L);
            return 1;
        });
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(1);
        when(certChangeLogMapper.selectLastRecordHash(1L)).thenReturn(null);
    }

    /** 打桩幂等窗口（秒）。 */
    @Test
    void getVerifyHistoryPage_mapsRowsAndBatchLoadsCertNames() {
        // 读出口：留痕要能读回来。同时钉住"证照名批量回填"（一次 in 查询），防止将来退化成逐行查。
        CertificateInfoDO cert = new CertificateInfoDO();
        cert.setId(1L);
        cert.setCertName("安全生产许可证");
        when(certificateInfoMapper.selectBatchIds(ArgumentMatchers.<Collection<Long>>any())).thenReturn(List.of(cert));

        CertVerifyHistoryDO matched = new CertVerifyHistoryDO();
        matched.setId(11L); matched.setCertId(1L); matched.setVerifyChannel("FOUR_PLATFORM");
        matched.setVerifyResult("MATCH"); matched.setVerifyBy(42L);
        matched.setVerifiedAt(LocalDateTime.of(2026, 9, 13, 10, 0));
        matched.setResultSnapshot("{\"result\":0}");
        CertVerifyHistoryDO unavailable = new CertVerifyHistoryDO();
        unavailable.setId(12L); unavailable.setCertId(1L); unavailable.setVerifyChannel("MINISTRY");
        unavailable.setVerifyResult("UNAVAILABLE"); unavailable.setVerifyBy(42L);
        unavailable.setVerifiedAt(LocalDateTime.of(2026, 9, 12, 9, 0));
        Page<CertVerifyHistoryDO> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of(matched, unavailable));
        dbPage.setTotal(2);
        when(certVerifyHistoryMapper.selectPage(ArgumentMatchers.<Page<CertVerifyHistoryDO>>any(),
                ArgumentMatchers.<Wrapper<CertVerifyHistoryDO>>any())).thenReturn(dbPage);

        CertVerifyHistoryPageReqVO reqVO = new CertVerifyHistoryPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<CertVerifyHistoryRespVO> result = service.getVerifyHistoryPage(reqVO);

        assertEquals(2L, result.getTotal());
        assertEquals("安全生产许可证", result.getList().get(0).getCertName(), "证照名应批量回填（人对 ID 不可读）");
        assertEquals(Boolean.TRUE, result.getList().get(0).getSnapshotAvailable(), "有报文留痕 → true（内容不下发）");
        assertEquals(Boolean.FALSE, result.getList().get(1).getSnapshotAvailable(), "无报文留痕 → false");
        assertEquals(LocalDateTime.of(2026, 9, 13, 10, 0), result.getList().get(0).getVerifiedAt());
        verify(certificateInfoMapper, times(1)).selectBatchIds(ArgumentMatchers.<Collection<Long>>any());
    }

    @Test
    void getVerifyHistoryPage_emptyResult_doesNotQueryCertNames() {
        Page<CertVerifyHistoryDO> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of());
        dbPage.setTotal(0);
        when(certVerifyHistoryMapper.selectPage(ArgumentMatchers.<Page<CertVerifyHistoryDO>>any(),
                ArgumentMatchers.<Wrapper<CertVerifyHistoryDO>>any())).thenReturn(dbPage);

        CertVerifyHistoryPageReqVO reqVO = new CertVerifyHistoryPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<CertVerifyHistoryRespVO> result = service.getVerifyHistoryPage(reqVO);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(certificateInfoMapper, never()).selectBatchIds(ArgumentMatchers.<Collection<Long>>any());
    }

    private void stubVerifyWindow(int seconds) {
        RuleConfigItemRespVO vo = new RuleConfigItemRespVO();
        vo.setRuleKey(RuleConfigKeys.CERT_VERIFY_IDEMPOTENT_WINDOW_SECONDS);
        vo.setValue(seconds);
        vo.setVersionNo(1);
        when(ruleConfigService.resolveWithAudit(eq(RuleConfigKeys.CERT_VERIFY_IDEMPOTENT_WINDOW_SECONDS),
                anyString())).thenReturn(vo);
    }

    /** 网关验真回执（与既有用例保持同一构造口径：head 两参、data 两参）。 */
    private EccertVerifyInfoRespVO verifyResp(String result) {
        return new EccertVerifyInfoRespVO(new EccertRespHeadVO("0", "调用成功"),
                new EccertVerifyInfoRespVO.Data(result, "测试回执"));
    }

    @Test
    void verify_verifyingStateRejected() {
        CertificateInfoDO cert = certOf("VERIFYING");
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.verifyCertificate(1L, "FOUR_PLATFORM", 42L));
        assertEquals(Integer.valueOf(1_060_109_012), e.getCode(), "验真进行中应报 CERT_VERIFY_IN_PROGRESS");
    }

    @Test
    void verify_invalidChannelRejected() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(certOf("UNVERIFIED"));
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.verifyCertificate(1L, "NOT_A_CHANNEL", 42L));
        assertEquals(Integer.valueOf(1_060_109_014), e.getCode(), "非法渠道应报 CERT_VERIFY_CHANNEL_INVALID");
        verify(certVerifyGateway, never()).verifyInfo(any());
    }

    @Test
    void verify_certNotExists() {
        when(certificateInfoMapper.selectById(9L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.verifyCertificate(9L, "FOUR_PLATFORM", 42L));
        assertEquals(Integer.valueOf(1_060_109_000), e.getCode());
    }

    @Test
    void verifyFile_currentFileVerified_marksVerified() {
        CertificateInfoDO cert = certOf("UNVERIFIED");
        CertFileVersionDO file = new CertFileVersionDO();
        file.setId(9L);
        file.setCertId(1L);
        file.setFileHash("ab".repeat(32));
        file.setFileFormat("PDF");
        file.setIsCurrent(true);
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert);
        when(certFileVersionMapper.selectAnyCurrentByCertId(1L)).thenReturn(file);
        when(certVerifyHistoryMapper.selectByIdempotentKey(any(String.class))).thenReturn(null);
        when(certVerifyGateway.verifyFile(any(EccertVerifyFileReqVO.class))).thenReturn(
                new EccertVerifyFileRespVO(new EccertRespHeadVO("0", "调用成功"),
                        new EccertVerifyFileRespVO.Data("0", "0 文件信息正确且有效")));
        when(certVerifyHistoryMapper.insert(any(CertVerifyHistoryDO.class))).thenAnswer(inv -> {
            CertVerifyHistoryDO h = inv.getArgument(0);
            h.setId(88L);
            return 1;
        });
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(1);

        Long id = service.verifyCertificateFile(1L, "FOUR_PLATFORM", 42L);

        assertEquals(88L, id);
        assertEquals(CertificateVerificationStatusEnum.VERIFIED.name(), cert.getVerificationStatus());
        assertEquals(CertVerifyResultEnum.MATCH.name(), historyOf().getVerifyResult());
        // 2026-09-13 起"种类"放在幂等键**键首**（FILE: / INFO:）：两条路径 verify_channel 相同，
        // 只有前缀能可靠隔离；用 contains(":FILE:") 会漏判（前缀之间也不允许互相包含）
        assertTrue(historyOf().getIdempotentKey().startsWith("FILE:1:FOUR_PLATFORM:"),
                "验文件的幂等键必须以 FILE: 前缀开头，实际=" + historyOf().getIdempotentKey());
        assertTrue(historyOf().getResultSnapshot().contains("\"method\":\"verifyfile\""));
        ArgumentCaptor<CertChangeLogDO> logs = ArgumentCaptor.forClass(CertChangeLogDO.class);
        verify(certChangeLogMapper).insert(logs.capture());
        assertEquals("VERIFY-FILE:88", logs.getValue().getBatchId());
    }

    @Test
    void verifyFile_noCurrentFile_rejects() {
        CertificateInfoDO cert = certOf("UNVERIFIED");
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert);
        when(certFileVersionMapper.selectAnyCurrentByCertId(1L)).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.verifyCertificateFile(1L, "FOUR_PLATFORM", 42L));
        assertEquals(Integer.valueOf(1_060_109_005), e.getCode(), "无生效文件应报 CERT_FILE_NOT_EXISTS");
        verify(certVerifyGateway, never()).verifyFile(any());
    }

    // ========== 工具 ==========

    private CertificateInfoDO certOf(String verificationStatus) {
        CertificateInfoDO c = new CertificateInfoDO();
        c.setId(1L);
        c.setCertName("建筑业企业资质证书");
        c.setCertNo("D123456789");
        c.setVerificationStatus(verificationStatus);
        c.setVersion(0);
        return c;
    }

    private CertVerifyHistoryDO historyOf() {
        ArgumentCaptor<CertVerifyHistoryDO> captor = ArgumentCaptor.forClass(CertVerifyHistoryDO.class);
        verify(certVerifyHistoryMapper).insert(captor.capture());
        return captor.getValue();
    }
}
