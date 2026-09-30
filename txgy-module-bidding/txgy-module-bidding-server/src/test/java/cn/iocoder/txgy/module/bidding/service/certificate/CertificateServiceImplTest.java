package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertChangeLogDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证照台账服务规则测试（Mockito，TDD）：
 * 建档判重 / 建档日志（CREATE·哈希链）/ 更新仅记差异字段 / 乐观锁预检与 CAS 冲突 /
 * 状态机合法与非法流转 / 吊销记 REVOKE。
 * 依据：《电子证件全流程管理_数据库设计.md》v3.3 §二/§13、《招投标模块开发规范》§4.5。
 */
class CertificateServiceImplTest {

    private CertificateInfoMapper certificateInfoMapper;
    private CertChangeLogMapper certChangeLogMapper;
    private cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService subjectProfileService;
    private cn.iocoder.txgy.module.bidding.service.review.ReviewTaskService reviewTaskService;
    private CertificateServiceImpl service;

    @Test
    void create_persistsOrgId_fromOwnSubject() {
        // 规范出处：契约里的 entId 类字段应由后端从"本租户主体档案"推导（一租户一家公司）。
        // 端到端实测：此前 org_id 恒为 NULL ⇒ 材料完备性永远查不到证照（营业执照恒报缺失）。
        subjectProfileService = mock(cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService.class);
        ReflectionTestUtils.setField(service, "subjectProfileService", subjectProfileService);
        when(subjectProfileService.getOwnSubjectId()).thenReturn(77L);
        when(certificateInfoMapper.insert(org.mockito.ArgumentMatchers.<CertificateInfoDO>any())).thenReturn(1);

        CertificateSaveReqVO reqVO = new CertificateSaveReqVO();
        reqVO.setHolderType("ORG");
        reqVO.setHolderName("端到端测试企业");
        reqVO.setCertType("ADMIN_LICENSE");
        reqVO.setCertName("营业执照");
        reqVO.setCertNo("91330100MA2E2E001X");
        service.createCertificate(reqVO);

        org.mockito.ArgumentCaptor<CertificateInfoDO> captor = org.mockito.ArgumentCaptor.forClass(CertificateInfoDO.class);
        verify(certificateInfoMapper).insert(captor.capture());
        assertEquals(77L, captor.getValue().getOrgId(), "org_id 必须取本租户主体档案 id");
        assertEquals(77L, captor.getValue().getHolderId(), "holder 缺省即本企业（前端不必传 id）");
    }

    @BeforeEach
    void setUp() {
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        certChangeLogMapper = mock(CertChangeLogMapper.class);
        service = new CertificateServiceImpl();
        ReflectionTestUtils.setField(service, "certificateInfoMapper", certificateInfoMapper);
        ReflectionTestUtils.setField(service, "certChangeLogMapper", certChangeLogMapper);
        subjectProfileService = mock(cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService.class);
        ReflectionTestUtils.setField(service, "subjectProfileService", subjectProfileService);
        // 审查任务服务（新依赖，契约《平台审查任务中心》：建档成功后自动生成审查任务）
        reviewTaskService = mock(cn.iocoder.txgy.module.bidding.service.review.ReviewTaskService.class);
        ReflectionTestUtils.setField(service, "reviewTaskService", reviewTaskService);
        // 字典解析器：toRespVO 会用它解析 certTypeName/certStatusName 等中文名。
        // 这里注入 mock（返回 null）——中文名属展示增强，本类用例断言的是业务字段映射，
        // 真实字典解析另有集成验证；不注入会 NPE（2026-09-21 实测）。
        cn.iocoder.txgy.module.bidding.enums.certificate.CertDictHelper certDictHelper =
                mock(cn.iocoder.txgy.module.bidding.enums.certificate.CertDictHelper.class);
        ReflectionTestUtils.setField(service, "certDictHelper", certDictHelper);
        // 企业锚点由后端推导：既有用例不关心具体值，给个宽松默认（新用例再覆盖）
        org.mockito.Mockito.lenient().when(subjectProfileService.getOwnSubjectId()).thenReturn(1L);
    }

    @Test
    void create_ok_returnsIdAndLogsCreateChain() {
        when(certificateInfoMapper.selectByCertNo("XZ-2026-001")).thenReturn(null);
        when(certChangeLogMapper.selectLastRecordHash(any())).thenReturn(null);
        when(certificateInfoMapper.insert(any(CertificateInfoDO.class))).thenAnswer(inv -> {
            CertificateInfoDO d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });

        Long id = service.createCertificate(req("XZ-2026-001"));

        assertEquals(1L, id);
        ArgumentCaptor<CertificateInfoDO> cert = ArgumentCaptor.forClass(CertificateInfoDO.class);
        verify(certificateInfoMapper).insert(cert.capture());
        assertEquals(CertificateStatusEnum.ACTIVE.name(), cert.getValue().getCertStatus());
        assertEquals("ORG", cert.getValue().getHolderType());

        // CREATE 日志 4 行（cert_no/cert_name/holder_name/cert_status），首条 prev_hash=ROOT、链式连接、哈希互不相同
        ArgumentCaptor<CertChangeLogDO> logs = ArgumentCaptor.forClass(CertChangeLogDO.class);
        verify(certChangeLogMapper, times(4)).insert(logs.capture());
        List<CertChangeLogDO> rows = logs.getAllValues();
        assertEquals(CertificateChangeTypeEnum.CREATE.name(), rows.get(0).getChangeType());
        assertEquals(1L, rows.get(0).getCertId());
        assertEquals("ROOT", rows.get(0).getPrevHash());
        assertEquals(64, rows.get(0).getRecordHash().length());
        for (int i = 1; i < rows.size(); i++) {
            assertEquals(rows.get(i - 1).getRecordHash(), rows.get(i).getPrevHash(), "哈希链必须逐条链接");
            assertNotEquals(rows.get(i - 1).getRecordHash(), rows.get(i).getRecordHash(), "不同行哈希不应相同");
        }
    }

    @Test
    void create_duplicateCertNoRejected() {
        when(certificateInfoMapper.selectByCertNo("XZ-2026-001")).thenReturn(certOf(9L, "XZ-2026-001",
                CertificateStatusEnum.ACTIVE));

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.createCertificate(req("XZ-2026-001")));

        assertEquals(Integer.valueOf(1_060_109_001), e.getCode(), "重复证书编号应报 CERTIFICATE_NO_DUPLICATE");
        verify(certificateInfoMapper, never()).insert(any(CertificateInfoDO.class));
    }

    @Test
    void update_onlyDiffsFieldsLogged() {
        CertificateInfoDO db = certOf(7L, "XZ-2026-001", CertificateStatusEnum.ACTIVE);
        db.setVersion(3);
        db.setRemark("旧备注");
        db.setCertLevel("一级");
        when(certificateInfoMapper.selectById(7L)).thenReturn(db);
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(1);

        CertificateSaveReqVO reqVO = req("XZ-2026-001");
        reqVO.setId(7L);
        reqVO.setVersion(3);
        reqVO.setRemark("新备注");
        reqVO.setCertLevel("一级"); // 未变字段不应产生日志行
        reqVO.setValidTo(LocalDate.of(2029, 12, 31));
        service.updateCertificate(reqVO);

        ArgumentCaptor<CertChangeLogDO> logs = ArgumentCaptor.forClass(CertChangeLogDO.class);
        verify(certChangeLogMapper, times(2)).insert(logs.capture());
        List<CertChangeLogDO> rows = logs.getAllValues();
        assertTrue(rows.stream().anyMatch(r -> "remark".equals(r.getFieldName())
                && "旧备注".equals(r.getOldValue()) && "新备注".equals(r.getNewValue())), "remark 变化应记录");
        assertTrue(rows.stream().anyMatch(r -> "valid_to".equals(r.getFieldName())), "valid_to 变化应记录");
        assertTrue(rows.stream().noneMatch(r -> "cert_level".equals(r.getFieldName())), "未变字段不得记录");
        assertEquals(CertificateChangeTypeEnum.UPDATE.name(), rows.get(0).getChangeType());
    }

    @Test
    void update_versionMismatchRejectedBeforeWrite() {
        CertificateInfoDO db = certOf(7L, "XZ-2026-001", CertificateStatusEnum.ACTIVE);
        db.setVersion(3);
        when(certificateInfoMapper.selectById(7L)).thenReturn(db);

        CertificateSaveReqVO reqVO = req("XZ-2026-001");
        reqVO.setId(7L);
        reqVO.setVersion(2); // 页面回传的旧版本
        reqVO.setRemark("新备注");

        ServiceException e = assertThrows(ServiceException.class, () -> service.updateCertificate(reqVO));
        assertEquals(Integer.valueOf(1_060_109_002), e.getCode(), "版本不一致应报 CERTIFICATE_DATA_MODIFIED");
        verify(certificateInfoMapper, never()).updateById(any(CertificateInfoDO.class));
    }

    @Test
    void update_conflictOnCasReturnsZeroRows() {
        CertificateInfoDO db = certOf(7L, "XZ-2026-001", CertificateStatusEnum.ACTIVE);
        db.setVersion(3);
        when(certificateInfoMapper.selectById(7L)).thenReturn(db);
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(0); // CAS 命中 0 行

        CertificateSaveReqVO reqVO = req("XZ-2026-001");
        reqVO.setId(7L);
        reqVO.setVersion(3);
        reqVO.setRemark("并发修改");

        ServiceException e = assertThrows(ServiceException.class, () -> service.updateCertificate(reqVO));
        assertEquals(Integer.valueOf(1_060_109_002), e.getCode(), "CAS 0 行应报 CERTIFICATE_DATA_MODIFIED");
    }

    @Test
    void changeStatus_validTransitionFromActiveToSuspended() {
        CertificateInfoDO db = certOf(7L, "XZ-2026-001", CertificateStatusEnum.ACTIVE);
        db.setVersion(5);
        when(certificateInfoMapper.selectById(7L)).thenReturn(db);
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(1);

        service.changeStatus(7L, CertificateStatusEnum.SUSPENDED.name(), 5, null,
                CertificateChangeSourceEnum.SYSTEM, "::1");

        assertEquals(CertificateStatusEnum.SUSPENDED.name(), db.getCertStatus());
        ArgumentCaptor<CertChangeLogDO> logs = ArgumentCaptor.forClass(CertChangeLogDO.class);
        verify(certChangeLogMapper).insert(logs.capture());
        assertEquals(CertificateChangeTypeEnum.STATUS_CHANGE.name(), logs.getValue().getChangeType());
        assertEquals("cert_status", logs.getValue().getFieldName());
        assertEquals("ACTIVE", logs.getValue().getOldValue());
        assertEquals("SUSPENDED", logs.getValue().getNewValue());
        assertEquals("::1", logs.getValue().getOpIp());
    }

    @Test
    void changeStatus_invalidTransitionRejected() {
        CertificateInfoDO db = certOf(7L, "XZ-2026-001", CertificateStatusEnum.CANCELLED); // 终态
        db.setVersion(2);
        when(certificateInfoMapper.selectById(7L)).thenReturn(db);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.changeStatus(7L, CertificateStatusEnum.ACTIVE.name(), 2, null,
                        CertificateChangeSourceEnum.MANUAL, null));
        assertEquals(Integer.valueOf(1_060_109_003), e.getCode(), "终态不可恢复应报状态流转非法");
        verify(certificateInfoMapper, never()).updateById(any(CertificateInfoDO.class));
    }

    @Test
    void changeStatus_revokeLogsReasonAndRevokeType() {
        CertificateInfoDO db = certOf(7L, "XZ-2026-001", CertificateStatusEnum.ACTIVE);
        db.setVersion(0);
        when(certificateInfoMapper.selectById(7L)).thenReturn(db);
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(1);

        service.changeStatus(7L, CertificateStatusEnum.REVOKED.name(), 0, "存在弄虚作假行为",
                CertificateChangeSourceEnum.MANUAL, "10.1.2.3");

        assertEquals(CertificateStatusEnum.REVOKED.name(), db.getCertStatus());
        ArgumentCaptor<CertChangeLogDO> logs = ArgumentCaptor.forClass(CertChangeLogDO.class);
        verify(certChangeLogMapper, times(2)).insert(logs.capture()); // cert_status + reason 两行
        List<CertChangeLogDO> rows = logs.getAllValues();
        assertEquals(CertificateChangeTypeEnum.REVOKE.name(), rows.get(0).getChangeType());
        assertEquals("存在弄虚作假行为", rows.get(1).getNewValue());
        assertEquals(rows.get(0).getRecordHash(), rows.get(1).getPrevHash(), "同一批次日志链式连接");
    }

    @Test
    void changeStatus_sameStatusIsIdempotentNoOp() {
        CertificateInfoDO db = certOf(7L, "XZ-2026-001", CertificateStatusEnum.ACTIVE);
        db.setVersion(1);
        when(certificateInfoMapper.selectById(7L)).thenReturn(db);

        service.changeStatus(7L, CertificateStatusEnum.ACTIVE.name(), 1, null,
                CertificateChangeSourceEnum.SYSTEM, null);

        verify(certificateInfoMapper, never()).updateById(any(CertificateInfoDO.class));
        verify(certChangeLogMapper, never()).insert(any(CertChangeLogDO.class));
    }

    @Test
    void get_ok_mapsDetailWithVersion() {
        CertificateInfoDO db = certOf(7L, "XZ-2026-001", CertificateStatusEnum.ACTIVE);
        db.setVersion(4);
        db.setECertIdent("ZZBH1234567890");
        when(certificateInfoMapper.selectById(7L)).thenReturn(db);

        CertificateRespVO resp = service.getCertificate(7L);

        assertEquals(7L, resp.getId());
        assertEquals(Integer.valueOf(4), resp.getVersion(), "详情应回传乐观锁版本供更新");
        assertEquals("ZZBH1234567890", resp.getECertIdent());
    }

    @Test
    void get_notExistsThrows() {
        when(certificateInfoMapper.selectById(7L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.getCertificate(7L));
        assertEquals(Integer.valueOf(1_060_109_000), e.getCode());
    }

    @Test
    void page_mapsRecordsAndTotal() {
        CertificateInfoDO a = certOf(1L, "XZ-2026-001", CertificateStatusEnum.ACTIVE);
        a.setVersion(0);
        CertificateInfoDO b = certOf(2L, "XZ-2026-002", CertificateStatusEnum.EXPIRED);
        b.setVersion(1);
        Page<CertificateInfoDO> dbPage = new Page<>(1, 10, 2);
        dbPage.setRecords(new ArrayList<>(List.of(a, b)));
        when(certificateInfoMapper.selectPage(any(Page.class), any())).thenReturn(dbPage);

        CertificatePageReqVO reqVO = new CertificatePageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<CertificateRespVO> result = service.getCertificatePage(reqVO);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals("XZ-2026-001", result.getList().get(0).getCertNo());
        assertEquals("EXPIRED", result.getList().get(1).getCertStatus());
    }

    // ========== 工具 ==========

    private CertificateSaveReqVO req(String certNo) {
        CertificateSaveReqVO reqVO = new CertificateSaveReqVO();
        reqVO.setCertNo(certNo);
        reqVO.setCertName("建筑业企业资质证书");
        reqVO.setCertType("ADMIN_LICENSE");
        reqVO.setHolderName("××建设工程有限公司");
        return reqVO;
    }

    private CertificateInfoDO certOf(Long id, String certNo, CertificateStatusEnum status) {
        CertificateInfoDO cert = new CertificateInfoDO();
        cert.setId(id);
        cert.setCertNo(certNo);
        cert.setCertName("建筑业企业资质证书");
        cert.setCertStatus(status.name());
        return cert;
    }
}
