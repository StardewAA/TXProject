package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleApplyReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.CertLifecycleRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.CertLifecycleRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.enums.CertLifecycleStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证件/资质全生命周期服务测试（Mockito，TDD）。
 *
 * <p>依据：《电子证件全流程管理_数据库设计.md》v3.3 表 3/5/6/7（四类流程与生效回写）；
 * 用户范围口径"做到证照全生命周期管理（中级）——变更、注销、年检、到期提醒、版本管理，不做签发"；
 * 开发规范 §4.5（状态机 + 乐观锁 CAS）。
 * <p>覆盖：变更空清单拒绝、续期缺新有效期拒绝、幂等键重复发起拒绝、非法状态流转拒绝、
 * 未审核直接生效拒绝、续期生效回写主档有效期与状态、变更按组回写多字段、二次生效拒绝。
 */
class CertLifecycleServiceImplTest {

    private CertLifecycleRecordMapper certLifecycleRecordMapper;
    private CertificateInfoMapper certificateInfoMapper;
    private PersonnelCertificateMapper personnelCertificateMapper;
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    private CertLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        certLifecycleRecordMapper = mock(CertLifecycleRecordMapper.class);
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        personnelCertificateMapper = mock(PersonnelCertificateMapper.class);
        enterpriseQualificationMapper = mock(EnterpriseQualificationMapper.class);
        qualificationSafetyPermitMapper = mock(QualificationSafetyPermitMapper.class);
        service = new CertLifecycleServiceImpl();
        ReflectionTestUtils.setField(service, "certLifecycleRecordMapper", certLifecycleRecordMapper);
        ReflectionTestUtils.setField(service, "certificateInfoMapper", certificateInfoMapper);
        ReflectionTestUtils.setField(service, "personnelCertificateMapper", personnelCertificateMapper);
        ReflectionTestUtils.setField(service, "enterpriseQualificationMapper", enterpriseQualificationMapper);
        ReflectionTestUtils.setField(service, "qualificationSafetyPermitMapper", qualificationSafetyPermitMapper);
    }

    private CertificateInfoDO cert(Long id, String status, LocalDate validTo) {
        CertificateInfoDO c = new CertificateInfoDO();
        c.setId(id);
        c.setOrgId(1L);
        c.setCertName("营业执照");
        c.setCertNo("91610000MA6XXXXXXX");
        c.setCertStatus(status);
        c.setValidFrom(LocalDate.now().minusYears(3));
        c.setValidTo(validTo);
        c.setVersion(0);
        return c;
    }

    private CertLifecycleApplyReqVO base(String type, Long bizId) {
        CertLifecycleApplyReqVO vo = new CertLifecycleApplyReqVO();
        vo.setBizDomain("CERT");
        vo.setBizId(bizId);
        vo.setLifecycleType(type);
        vo.setApplyNo("BL-TEST-001");
        return vo;
    }

    // ---------- 发起 ----------

    @Test
    void apply_change_rejectsEmptyChangeList() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert(1L, "ACTIVE", LocalDate.now().plusYears(1)));

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.apply(base("CHANGE", 1L)));
        assertEquals(ErrorCodeConstants.CERT_LIFECYCLE_CHANGE_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void apply_change_insertsOneRowPerField_sharingGroupId() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert(1L, "ACTIVE", LocalDate.now().plusYears(1)));
        when(certLifecycleRecordMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(certLifecycleRecordMapper.insert(any(CertLifecycleRecordDO.class))).thenAnswer(inv -> {
            CertLifecycleRecordDO d = inv.getArgument(0);
            d.setId(100L + Math.abs(d.getFieldName().hashCode() % 100));
            return 1;
        });

        CertLifecycleApplyReqVO vo = base("CHANGE", 1L);
        CertLifecycleApplyReqVO.FieldChange f1 = new CertLifecycleApplyReqVO.FieldChange();
        f1.setFieldName("certName");
        f1.setOldValue("旧名称");
        f1.setNewValue("新名称");
        CertLifecycleApplyReqVO.FieldChange f2 = new CertLifecycleApplyReqVO.FieldChange();
        f2.setFieldName("certLevel");
        f2.setNewValue("一级");
        vo.setChanges(List.of(f1, f2));

        service.apply(vo);

        ArgumentCaptor<CertLifecycleRecordDO> cap = ArgumentCaptor.forClass(CertLifecycleRecordDO.class);
        verify(certLifecycleRecordMapper, times(2)).insert(cap.capture());
        // 一次变更多字段必须共用同一个归组号，否则生效时无法整体回写
        assertNotNull(cap.getAllValues().get(0).getChangeGroupId());
        assertEquals(cap.getAllValues().get(0).getChangeGroupId(), cap.getAllValues().get(1).getChangeGroupId());
        assertEquals(CertLifecycleStatusEnum.PENDING.name(), cap.getAllValues().get(0).getStatus());
    }

    @Test
    void apply_renew_rejectsMissingNewValidTo() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert(1L, "ACTIVE", LocalDate.now()));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.apply(base("RENEW", 1L)));
        assertEquals(ErrorCodeConstants.CERT_LIFECYCLE_TARGET_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void apply_rejectsDuplicateApplyNo_viaIdempotencyKey() {
        when(certificateInfoMapper.selectById(1L)).thenReturn(cert(1L, "ACTIVE", LocalDate.now()));
        when(certLifecycleRecordMapper.selectByIdempotentKey(anyString()))
                .thenReturn(new CertLifecycleRecordDO());

        CertLifecycleApplyReqVO vo = base("RENEW", 1L);
        vo.setNewValidTo(LocalDate.now().plusYears(3));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.apply(vo));
        assertEquals(ErrorCodeConstants.CERT_LIFECYCLE_APPLY_NO_DUPLICATE.getCode(), ex.getCode());
        verify(certLifecycleRecordMapper, never()).insert(any(CertLifecycleRecordDO.class));
    }

    @Test
    void apply_rejectsUnknownBizDomain() {
        CertLifecycleApplyReqVO vo = base("RENEW", 1L);
        vo.setBizDomain("NOT_EXIST");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.apply(vo));
        assertEquals(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID.getCode(), ex.getCode());
    }

    // ---------- 审核 ----------

    @Test
    void audit_rejectsPendingToEffectiveJump() {
        // "待办 → 直接生效"必须被拒：否则审批留痕形同虚设
        CertLifecycleRecordDO record = new CertLifecycleRecordDO();
        record.setId(10L);
        record.setStatus(CertLifecycleStatusEnum.PENDING.name());
        record.setVersion(0);
        when(certLifecycleRecordMapper.selectById(10L)).thenReturn(record);

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.audit(10L, CertLifecycleStatusEnum.EFFECTIVE.name(), 0, "直接生效"));
        assertEquals(ErrorCodeConstants.CERT_LIFECYCLE_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void audit_ok_pendingToApproved() {
        CertLifecycleRecordDO record = new CertLifecycleRecordDO();
        record.setId(10L);
        record.setStatus(CertLifecycleStatusEnum.PENDING.name());
        record.setVersion(0);
        when(certLifecycleRecordMapper.selectById(10L)).thenReturn(record);
        when(certLifecycleRecordMapper.updateById(any(CertLifecycleRecordDO.class))).thenReturn(1);

        service.audit(10L, CertLifecycleStatusEnum.APPROVED.name(), 0, "同意");

        ArgumentCaptor<CertLifecycleRecordDO> cap = ArgumentCaptor.forClass(CertLifecycleRecordDO.class);
        verify(certLifecycleRecordMapper).updateById(cap.capture());
        assertEquals(CertLifecycleStatusEnum.APPROVED.name(), cap.getValue().getStatus());
    }

    // ---------- 生效回写 ----------

    @Test
    void effect_renew_writesBackValidityAndStatus() {
        CertificateInfoDO target = cert(1L, "EXPIRED", LocalDate.now().minusDays(1));
        when(certificateInfoMapper.selectById(1L)).thenReturn(target);
        when(certificateInfoMapper.updateById(any(CertificateInfoDO.class))).thenReturn(1);
        CertLifecycleRecordDO record = new CertLifecycleRecordDO();
        record.setId(20L);
        record.setBizDomain("CERT");
        record.setBizId(1L);
        record.setLifecycleType("RENEW");
        record.setStatus(CertLifecycleStatusEnum.APPROVED.name());
        record.setVersion(0);
        record.setNewValidFrom(LocalDate.now());
        record.setNewValidTo(LocalDate.now().plusYears(3));
        when(certLifecycleRecordMapper.selectById(20L)).thenReturn(record);
        when(certLifecycleRecordMapper.updateById(any(CertLifecycleRecordDO.class))).thenReturn(1);

        service.effect(20L, 0, "延续办结");

        ArgumentCaptor<CertificateInfoDO> cap = ArgumentCaptor.forClass(CertificateInfoDO.class);
        verify(certificateInfoMapper).updateById(cap.capture());
        assertEquals(LocalDate.now().plusYears(3), cap.getValue().getValidTo());
        // 续期即"重新有效"：状态必须拉回 ACTIVE，否则台账会停留在 EXPIRED 继续被误判
        assertEquals("ACTIVE", cap.getValue().getCertStatus());
    }

    @Test
    void effect_change_writesBackAllFieldsInGroup() {
        PersonnelCertificateDO target = new PersonnelCertificateDO();
        target.setId(5L);
        target.setCertName("旧证书名");
        target.setCertLevel("二级");
        target.setVersion(0);
        when(personnelCertificateMapper.selectById(5L)).thenReturn(target);
        when(personnelCertificateMapper.updateById(any(PersonnelCertificateDO.class))).thenReturn(1);

        CertLifecycleRecordDO row1 = new CertLifecycleRecordDO();
        row1.setId(30L);
        row1.setBizDomain("PERSON_CERT");
        row1.setBizId(5L);
        row1.setLifecycleType("CHANGE");
        row1.setChangeGroupId("G-1");
        row1.setFieldName("certName");
        row1.setNewValue("一级建造师注册证书");
        row1.setStatus(CertLifecycleStatusEnum.APPROVED.name());
        row1.setVersion(0);
        CertLifecycleRecordDO row2 = new CertLifecycleRecordDO();
        row2.setId(31L);
        row2.setFieldName("certLevel");
        row2.setNewValue("一级");
        row2.setBizDomain("PERSON_CERT");
        row2.setBizId(5L);
        row2.setLifecycleType("CHANGE");
        row2.setChangeGroupId("G-1");
        row2.setStatus(CertLifecycleStatusEnum.APPROVED.name());
        row2.setVersion(0);

        when(certLifecycleRecordMapper.selectById(30L)).thenReturn(row1);
        when(certLifecycleRecordMapper.selectByChangeGroup("G-1")).thenReturn(List.of(row1, row2));
        when(certLifecycleRecordMapper.updateById(any(CertLifecycleRecordDO.class))).thenReturn(1);

        service.effect(30L, 0, "变更生效");

        ArgumentCaptor<PersonnelCertificateDO> cap = ArgumentCaptor.forClass(PersonnelCertificateDO.class);
        verify(personnelCertificateMapper).updateById(cap.capture());
        assertEquals("一级建造师注册证书", cap.getValue().getCertName());
        assertEquals("一级", cap.getValue().getCertLevel());
        verify(certLifecycleRecordMapper, times(2)).updateById(any(CertLifecycleRecordDO.class));
    }

    @Test
    void effect_rejectsDoubleEffect() {
        CertLifecycleRecordDO record = new CertLifecycleRecordDO();
        record.setId(40L);
        record.setStatus(CertLifecycleStatusEnum.EFFECTIVE.name());
        record.setVersion(1);
        when(certLifecycleRecordMapper.selectById(40L)).thenReturn(record);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.effect(40L, 1, "再次生效"));
        assertEquals(ErrorCodeConstants.CERT_LIFECYCLE_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void effect_rejectsStaleVersion() {
        CertLifecycleRecordDO record = new CertLifecycleRecordDO();
        record.setId(41L);
        record.setStatus(CertLifecycleStatusEnum.APPROVED.name());
        record.setVersion(2);
        when(certLifecycleRecordMapper.selectById(41L)).thenReturn(record);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.effect(41L, 1, "并发"));
        assertEquals(ErrorCodeConstants.CERT_LIFECYCLE_DATA_MODIFIED.getCode(), ex.getCode());
    }
}
