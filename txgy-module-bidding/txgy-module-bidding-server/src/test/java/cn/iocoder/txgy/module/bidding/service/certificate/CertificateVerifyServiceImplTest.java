package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertReqHeadVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertRespHeadVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileRespVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertChangeLogDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertVerifyHistoryDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertFileVersionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertVerifyHistoryMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertVerifyResultEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateVerificationStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void verify_sameMinuteRetry_returnsExistingHistoryWithoutGatewayCall() {
        CertificateInfoDO cert = certOf("UNVERIFIED");
        CertVerifyHistoryDO existing = new CertVerifyHistoryDO();
        existing.setId(5L);
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert);
        when(certVerifyHistoryMapper.selectByIdempotentKey(any(String.class))).thenReturn(existing);

        Long id = service.verifyCertificate(1L, "FOUR_PLATFORM", 42L);

        assertEquals(5L, id, "同分钟重试应幂等返回既有历史");
        verify(certVerifyGateway, never()).verifyInfo(any());
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
        assertTrue(historyOf().getIdempotentKey().contains(":FILE:"), "FILE 段与 verifyinfo 隔离");
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
