package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationApplyRecordDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationApplyRecordMapper;
import cn.iocoder.txgy.module.bidding.enums.QualificationApplyStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationApplyTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业资质主档/申报流服务规则测试（Mockito，TDD）：
 * 建档 UK 判重/关联台账校验、状态机非法流转、延续申报（PENDING_RENEW）、审批通过办结回写（VALID+new_valid_to）、
 * 退回还原、乐观锁冲突。依据：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1/15-7。
 */
class EnterpriseQualificationServiceImplTest {

    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    private QualificationApplyRecordMapper qualificationApplyRecordMapper;
    private CertificateInfoMapper certificateInfoMapper;
    private EnterpriseQualificationServiceImpl service;

    @BeforeEach
    void setUp() {
        enterpriseQualificationMapper = mock(EnterpriseQualificationMapper.class);
        qualificationApplyRecordMapper = mock(QualificationApplyRecordMapper.class);
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        service = new EnterpriseQualificationServiceImpl();
        ReflectionTestUtils.setField(service, "enterpriseQualificationMapper", enterpriseQualificationMapper);
        ReflectionTestUtils.setField(service, "qualificationApplyRecordMapper", qualificationApplyRecordMapper);
        ReflectionTestUtils.setField(service, "certificateInfoMapper", certificateInfoMapper);
    }

    @Test
    void create_ok_returnsIdWithDefaults() {
        when(enterpriseQualificationMapper.selectByCertNo(any(), any(), any(), any(), any())).thenReturn(null);
        when(enterpriseQualificationMapper.insert(any(EnterpriseQualificationDO.class))).thenAnswer(inv -> {
            EnterpriseQualificationDO d = inv.getArgument(0);
            d.setId(31L);
            return 1;
        });

        Long id = service.createQualification(req(null));

        assertEquals(31L, id);
        ArgumentCaptor<EnterpriseQualificationDO> captor = ArgumentCaptor.forClass(EnterpriseQualificationDO.class);
        verify(enterpriseQualificationMapper).insert(captor.capture());
        assertEquals(QualificationStatusEnum.VALID.name(), captor.getValue().getQualStatus());
        assertEquals("MANUAL", captor.getValue().getSourceChannel());
        assertEquals("CONSTRUCTION", captor.getValue().getQualSerial());
    }

    @Test
    void create_duplicateUkRejected() {
        when(enterpriseQualificationMapper.selectByCertNo(any(), any(), any(), any(), any()))
                .thenReturn(new EnterpriseQualificationDO());

        ServiceException e = assertThrows(ServiceException.class, () -> service.createQualification(req(null)));
        assertEquals(Integer.valueOf(1_060_106_016), e.getCode(), "UK 命中应报 QUALIFICATION_NO_DUPLICATE");
        verify(enterpriseQualificationMapper, never()).insert(any(EnterpriseQualificationDO.class));
    }

    @Test
    void create_bindedCertNotExistsRejected() {
        when(enterpriseQualificationMapper.selectByCertNo(any(), any(), any(), any(), any())).thenReturn(null);
        when(certificateInfoMapper.selectById(88L)).thenReturn(null);

        EnterpriseQualificationSaveReqVO r = req(null);
        r.setBindedCertId(88L);
        ServiceException e = assertThrows(ServiceException.class, () -> service.createQualification(r));
        assertEquals(Integer.valueOf(1_060_106_000), e.getCode(), "关联台账证书不存在应报 CERTIFICATE_NOT_EXISTS");
    }

    @Test
    void create_bindedCertExistsPasses() {
        when(enterpriseQualificationMapper.selectByCertNo(any(), any(), any(), any(), any())).thenReturn(null);
        when(certificateInfoMapper.selectById(88L)).thenReturn(new CertificateInfoDO());
        when(enterpriseQualificationMapper.insert(any(EnterpriseQualificationDO.class))).thenAnswer(inv -> {
            EnterpriseQualificationDO d = inv.getArgument(0);
            d.setId(31L);
            return 1;
        });
        EnterpriseQualificationSaveReqVO r = req(null);
        r.setBindedCertId(88L);
        assertEquals(31L, service.createQualification(r));
    }

    @Test
    void changeStatus_invalidTransitionRejected() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.REVOKED); // 终态
        qual.setVersion(1);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.changeStatus(1L, QualificationStatusEnum.VALID.name(), 1, null, 42L));
        assertEquals(Integer.valueOf(1_060_106_017), e.getCode(), "终态不可恢复应报状态流转非法");
        verify(enterpriseQualificationMapper, never()).updateById(any(EnterpriseQualificationDO.class));
    }

    @Test
    void changeStatus_versionMismatchRejected() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.VALID);
        qual.setVersion(5);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.changeStatus(1L, QualificationStatusEnum.EXPIRED.name(), 2, null, null));
        // 版本先于枚举解析校验：确保 version 冲突在迁移表解析前拦截
        assertEquals(Integer.valueOf(1_060_106_018), e.getCode());
    }

    @Test
    void submitRenewal_createsApplyAndMarksPendingRenew() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.VALID);
        qual.setVersion(3);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);
        when(qualificationApplyRecordMapper.insert(any(QualificationApplyRecordDO.class))).thenAnswer(inv -> {
            QualificationApplyRecordDO a = inv.getArgument(0);
            a.setId(50L);
            return 1;
        });
        when(enterpriseQualificationMapper.updateById(any(EnterpriseQualificationDO.class))).thenReturn(1);

        Long applyId = service.submitRenewalApply(1L, 3, 42L,
                LocalDate.of(2026, 10, 31), "证书到期延续申报");

        assertEquals(50L, applyId);
        assertEquals(QualificationStatusEnum.PENDING_RENEW.name(), qual.getQualStatus());
        ArgumentCaptor<QualificationApplyRecordDO> captor = ArgumentCaptor.forClass(QualificationApplyRecordDO.class);
        verify(qualificationApplyRecordMapper).insert(captor.capture());
        assertEquals(QualificationApplyTypeEnum.RENEW.name(), captor.getValue().getApplyType());
        assertEquals(QualificationApplyStatusEnum.SUBMITTED.name(), captor.getValue().getStatus());
        assertEquals(42L, captor.getValue().getOwnerBy());
    }

    @Test
    void submitRenewal_fromRevokedRejected() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.REVOKED);
        qual.setVersion(0);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.submitRenewalApply(1L, 0, 42L, null, null));
        assertEquals(Integer.valueOf(1_060_106_017), e.getCode());
        verify(qualificationApplyRecordMapper, never()).insert(any(QualificationApplyRecordDO.class));
    }

    @Test
    void approveRenewal_writesBackValidAndNewValidTo() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.PENDING_RENEW);
        qual.setVersion(2);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);
        QualificationApplyRecordDO apply = applyOf(50L, QualificationApplyStatusEnum.SUBMITTED);
        apply.setVersion(0);
        when(qualificationApplyRecordMapper.selectById(50L)).thenReturn(apply);
        when(qualificationApplyRecordMapper.updateById(any(QualificationApplyRecordDO.class))).thenReturn(1);
        when(enterpriseQualificationMapper.updateById(any(EnterpriseQualificationDO.class))).thenReturn(1);

        Long id = service.approveRenewalApply(50L, 0, "建审〔2026〕99号", "D-NEW-001",
                LocalDate.of(2029, 12, 31), 7L);

        assertEquals(50L, id);
        assertEquals(QualificationStatusEnum.VALID.name(), qual.getQualStatus(), "办结回写 VALID");
        assertEquals(LocalDate.of(2029, 12, 31), qual.getValidTo(), "回写延续后 valid_to");
        assertEquals("D-NEW-001", qual.getCertNo());
        // 申报流：APPROVED→ISSUED 两次推进
        verify(qualificationApplyRecordMapper, times(2)).updateById(any(QualificationApplyRecordDO.class));
        assertEquals(QualificationApplyStatusEnum.ISSUED.name(), apply.getStatus());
        assertEquals("建审〔2026〕99号", apply.getResultNo());
    }

    @Test
    void approveRenewal_missingNewValidToRejected() {
        QualificationApplyRecordDO apply = applyOf(50L, QualificationApplyStatusEnum.SUBMITTED);
        apply.setVersion(0);
        when(qualificationApplyRecordMapper.selectById(50L)).thenReturn(apply);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.approveRenewalApply(50L, 0, "文号", null, null, 7L));
        assertEquals(Integer.valueOf(1_060_106_021), e.getCode(), "延续必须给新有效期");
        verify(enterpriseQualificationMapper, never()).updateById(any(EnterpriseQualificationDO.class));
    }

    @Test
    void rejectRenewal_restoresValid() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.PENDING_RENEW);
        qual.setVersion(1);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);
        QualificationApplyRecordDO apply = applyOf(50L, QualificationApplyStatusEnum.SUBMITTED);
        apply.setVersion(0);
        when(qualificationApplyRecordMapper.selectById(50L)).thenReturn(apply);
        when(qualificationApplyRecordMapper.updateById(any(QualificationApplyRecordDO.class))).thenReturn(1);
        when(enterpriseQualificationMapper.updateById(any(EnterpriseQualificationDO.class))).thenReturn(1);

        service.rejectRenewalApply(50L, 0, 7L, "材料不齐");

        assertEquals(QualificationApplyStatusEnum.REJECTED.name(), apply.getStatus());
        assertEquals(QualificationStatusEnum.VALID.name(), qual.getQualStatus(), "退回还原 VALID");
        assertEquals("材料不齐", apply.getRemark());
    }

    // ========== 工具 ==========

    private EnterpriseQualificationSaveReqVO req(Long id) {
        EnterpriseQualificationSaveReqVO r = new EnterpriseQualificationSaveReqVO();
        r.setId(id);
        r.setEntId(1L);
        r.setQualSerial("CONSTRUCTION");
        r.setQualCategory("市政公用工程施工总承包");
        r.setQualGrade("一级");
        r.setCertNo("D123456789");
        r.setIssuingAuthority("××省住房和城乡建设厅");
        r.setValidTo(LocalDate.of(2029, 6, 30));
        return r;
    }

    private EnterpriseQualificationDO qualOf(QualificationStatusEnum status) {
        EnterpriseQualificationDO q = new EnterpriseQualificationDO();
        q.setId(1L);
        q.setEntId(1L);
        q.setQualSerial("CONSTRUCTION");
        q.setCertNo("D123456789");
        q.setQualStatus(status.name());
        return q;
    }

    private QualificationApplyRecordDO applyOf(Long id, QualificationApplyStatusEnum status) {
        QualificationApplyRecordDO a = new QualificationApplyRecordDO();
        a.setId(id);
        a.setEntId(1L);
        a.setQualId(1L);
        a.setApplyType(QualificationApplyTypeEnum.RENEW.name());
        a.setStatus(status.name());
        return a;
    }
}
