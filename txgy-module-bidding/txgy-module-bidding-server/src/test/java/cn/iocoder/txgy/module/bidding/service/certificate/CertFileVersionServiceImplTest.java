package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileRegisterReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertFileVersionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertFileCategoryEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateVirusScanStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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
 * 证照文件版本服务规则测试（Mockito，TDD）：
 * 首版登记/版本递增+旧当前行关闭/同哈希防重/证书不存在/类别校验/扫描 CLEAN 与 INFECTED 停用。
 * 依据：《电子证件全流程管理_数据库设计.md》v3.3 §三、《招投标模块开发规范》§5 三-5。
 */
class CertFileVersionServiceImplTest {

    private CertFileVersionMapper certFileVersionMapper;
    private CertificateInfoMapper certificateInfoMapper;
    private CertFileVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        certFileVersionMapper = mock(CertFileVersionMapper.class);
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        service = new CertFileVersionServiceImpl();
        ReflectionTestUtils.setField(service, "certFileVersionMapper", certFileVersionMapper);
        ReflectionTestUtils.setField(service, "certificateInfoMapper", certificateInfoMapper);
    }

    @Test
    void registerFirstFile_version1CurrentOnPending() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert());
        when(certFileVersionMapper.selectByCertIdAndHash(1L, "HASH-1")).thenReturn(null);
        when(certFileVersionMapper.selectMaxVersion(1L, CertFileCategoryEnum.ORIGINAL.name())).thenReturn(null);
        when(certFileVersionMapper.insert(any(CertFileVersionDO.class))).thenAnswer(inv -> {
            CertFileVersionDO d = inv.getArgument(0);
            d.setId(100L);
            return 1;
        });

        Long id = service.registerFile(req(1L, null, "HASH-1", "原件.pdf"));

        assertEquals(100L, id);
        ArgumentCaptor<CertFileVersionDO> captor = ArgumentCaptor.forClass(CertFileVersionDO.class);
        verify(certFileVersionMapper).insert(captor.capture());
        CertFileVersionDO file = captor.getValue();
        assertEquals(CertFileCategoryEnum.ORIGINAL.name(), file.getFileCategory(), "缺省类别应为 ORIGINAL");
        assertEquals(1, file.getVersion());
        assertTrue(file.getIsCurrent());
        assertEquals(CertificateVirusScanStatusEnum.PENDING.name(), file.getVirusScanStatus());
        assertEquals("HASH-1", file.getFileHash());
    }

    @Test
    void registerSecondFile_versionIncrementsAndOldCurrentOff() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert());
        when(certFileVersionMapper.selectByCertIdAndHash(1L, "HASH-2")).thenReturn(null);
        when(certFileVersionMapper.selectMaxVersion(1L, CertFileCategoryEnum.ORIGINAL.name())).thenReturn(1);
        CertFileVersionDO old = file(50L, 1, Boolean.TRUE);
        when(certFileVersionMapper.selectCurrentByCategory(1L, CertFileCategoryEnum.ORIGINAL.name()))
                .thenReturn(List.of(old));
        when(certFileVersionMapper.insert(any(CertFileVersionDO.class))).thenAnswer(inv -> {
            CertFileVersionDO d = inv.getArgument(0);
            d.setId(101L);
            return 1;
        });

        service.registerFile(req(1L, CertFileCategoryEnum.ORIGINAL.name(), "HASH-2", "原件v2.pdf"));

        verify(certFileVersionMapper).updateById(old); // 旧当前行关闭
        assertTrue(!old.getIsCurrent());
        ArgumentCaptor<CertFileVersionDO> captor = ArgumentCaptor.forClass(CertFileVersionDO.class);
        verify(certFileVersionMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getVersion(), "同类别版本号应递增");
    }

    @Test
    void registerDuplicateHashRejected() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert());
        when(certFileVersionMapper.selectByCertIdAndHash(1L, "HASH-1")).thenReturn(file(50L, 1, Boolean.TRUE));

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.registerFile(req(1L, null, "HASH-1", "原件.pdf")));

        assertEquals(Integer.valueOf(1_060_106_007), e.getCode(), "同哈希应报 CERT_FILE_HASH_DUPLICATE");
        verify(certFileVersionMapper, never()).insert(any(CertFileVersionDO.class));
    }

    @Test
    void registerCertNotExistsRejected() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.registerFile(req(1L, null, "HASH-1", "原件.pdf")));

        assertEquals(Integer.valueOf(1_060_106_000), e.getCode(), "证照不存在应报 CERTIFICATE_NOT_EXISTS");
        verify(certFileVersionMapper, never()).insert(any(CertFileVersionDO.class));
    }

    @Test
    void registerInvalidCategoryRejected() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert());

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.registerFile(req(1L, "NOT_A_CATEGORY", "HASH-1", "原件.pdf")));

        assertEquals(Integer.valueOf(1_060_106_009), e.getCode(), "非法类别应报 CERT_FILE_PARAM_INVALID");
    }

    @Test
    void registerCopyKeepsCopyMetadata() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert());
        when(certFileVersionMapper.selectByCertIdAndHash(1L, "HASH-C")).thenReturn(null);
        when(certFileVersionMapper.selectMaxVersion(1L, CertFileCategoryEnum.E_CERT.name())).thenReturn(null);
        when(certFileVersionMapper.insert(any(CertFileVersionDO.class))).thenAnswer(inv -> {
            CertFileVersionDO d = inv.getArgument(0);
            d.setId(102L);
            return 1;
        });

        CertFileRegisterReqVO r = req(1L, CertFileCategoryEnum.E_CERT.name(), "HASH-C", "加注件.ofd");
        r.setCopyCause("办理备案登记");
        r.setCopyCreator("××政务服务中心");
        service.registerFile(r);

        ArgumentCaptor<CertFileVersionDO> captor = ArgumentCaptor.forClass(CertFileVersionDO.class);
        verify(certFileVersionMapper).insert(captor.capture());
        assertEquals("办理备案登记", captor.getValue().getCopyCause(), "加注件事由应保留（GB/T 36903 §5.4）");
        assertEquals("××政务服务中心", captor.getValue().getCopyCreator());
    }

    @Test
    void markScanResult_cleanKeepsCurrent() {
        CertFileVersionDO file = file(50L, 1, Boolean.TRUE);
        when(certFileVersionMapper.selectById(50L)).thenReturn(file);

        service.markScanResult(50L, CertificateVirusScanStatusEnum.CLEAN.name());

        assertEquals(CertificateVirusScanStatusEnum.CLEAN.name(), file.getVirusScanStatus());
        assertTrue(file.getIsCurrent(), "CLEAN 不改变 is_current");
        verify(certFileVersionMapper).updateById(file);
    }

    @Test
    void markScanResult_infectedDisablesCurrent() {
        CertFileVersionDO file = file(50L, 1, Boolean.TRUE);
        when(certFileVersionMapper.selectById(50L)).thenReturn(file);

        service.markScanResult(50L, CertificateVirusScanStatusEnum.INFECTED.name());

        assertEquals(CertificateVirusScanStatusEnum.INFECTED.name(), file.getVirusScanStatus());
        assertTrue(!file.getIsCurrent(), "INFECTED 应停用当前版本（v3.3 §三）");
        verify(certFileVersionMapper).updateById(file);
    }

    @Test
    void markScanResult_notFound() {
        when(certFileVersionMapper.selectById(99L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.markScanResult(99L, CertificateVirusScanStatusEnum.CLEAN.name()));
        assertEquals(Integer.valueOf(1_060_106_005), e.getCode(), "应报 CERT_FILE_NOT_EXISTS");
    }

    // ========== 工具 ==========

    private CertFileRegisterReqVO req(Long certId, String category, String hash, String name) {
        CertFileRegisterReqVO r = new CertFileRegisterReqVO();
        r.setCertId(certId);
        r.setFileCategory(category);
        r.setFileId(9_000L);
        r.setFileHash(hash);
        r.setFileSize(1024L);
        r.setFileName(name);
        return r;
    }

    private CertificateInfoDO cert() {
        CertificateInfoDO c = new CertificateInfoDO();
        c.setId(1L);
        return c;
    }

    private CertFileVersionDO file(Long id, int version, Boolean isCurrent) {
        CertFileVersionDO f = new CertFileVersionDO();
        f.setId(id);
        f.setCertId(1L);
        f.setFileCategory(CertFileCategoryEnum.ORIGINAL.name());
        f.setFileHash("HASH");
        f.setVersion(version);
        f.setIsCurrent(isCurrent);
        return f;
    }
}
