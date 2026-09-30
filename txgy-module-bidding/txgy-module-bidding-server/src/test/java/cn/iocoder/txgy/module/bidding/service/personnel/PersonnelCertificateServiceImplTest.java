package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.PersonnelCertStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工证书服务规则测试（Mockito，TDD）。
 *
 * <p>依据：用户口径"员工证书集中管理、动态更新 + 到期自动提醒"；《电子证件全流程管理_数据库设计.md》
 * v3.3 表 15-3（注册有效期）、§九（到期预警依赖 valid_to）；06 文档 §二-1/§二-4（细分与复审）；
 * 开发规范 §4.5（状态流转 + 乐观锁）。
 * <p>覆盖：同人同类型同编号判重、有期限类型缺有效期拒绝、过期日期建档落 EXPIRED、
 * 状态机非法流转拒绝、EXPIRING_SOON 不接受人工设置、归属企业与档案不一致拒绝。
 */
class PersonnelCertificateServiceImplTest {

    private PersonnelCertificateMapper personnelCertificateMapper;
    private PersonnelProfileMapper personnelProfileMapper;
    private PersonnelCertificateServiceImpl service;

    @BeforeEach
    void setUp() {
        personnelCertificateMapper = mock(PersonnelCertificateMapper.class);
        personnelProfileMapper = mock(PersonnelProfileMapper.class);
        service = new PersonnelCertificateServiceImpl();
        ReflectionTestUtils.setField(service, "personnelCertificateMapper", personnelCertificateMapper);
        ReflectionTestUtils.setField(service, "personnelProfileMapper", personnelProfileMapper);
    }

    private PersonnelProfileDO profile(Long id, Long entId) {
        PersonnelProfileDO p = new PersonnelProfileDO();
        p.setId(id);
        p.setEntId(entId);
        p.setPersonnelStatus("ACTIVE");
        return p;
    }

    private PersonnelCertificateSaveReqVO req(String certType, String certNo, LocalDate validTo) {
        PersonnelCertificateSaveReqVO vo = new PersonnelCertificateSaveReqVO();
        vo.setEntId(1L);
        vo.setPersonId(100L);
        vo.setCertType(certType);
        vo.setCertName("一级建造师注册证书");
        vo.setCertNo(certNo);
        vo.setValidTo(validTo);
        return vo;
    }

    @Test
    void listByPerson_exposesIsCurrent_forReplacedCert() {
        // 契约 §8.1 承诺 isCurrent（"是否当前有效版本"）：按人查全部证书时，false 的行就是"被换证替换的旧证"，
        // 前端据此区分现行证/历史证。原实现只写不读（DO 有、VO 无），属"说得到、拿不到"。
        PersonnelCertificateDO replaced = new PersonnelCertificateDO();
        replaced.setId(501L);
        replaced.setPersonId(100L);
        replaced.setEntId(1L);
        replaced.setCertType("REG_PRACTICE_CERT");
        replaced.setCertStatus(PersonnelCertStatusEnum.EXPIRED.name());
        replaced.setValidTo(LocalDate.now().minusYears(3));
        replaced.setIsCurrent(false);
        when(personnelCertificateMapper.selectByPerson(100L)).thenReturn(List.of(replaced));

        List<PersonnelCertRespVO> list = service.listByPerson(100L);

        assertEquals(1, list.size());
        assertEquals(Boolean.FALSE, list.get(0).getIsCurrent(), "旧证必须回传 isCurrent=false（不得恒为 true）");
    }

    @Test
    void create_ok_insertsValidWithIsCurrent() {
        when(personnelProfileMapper.selectById(100L)).thenReturn(profile(100L, 1L));
        when(personnelCertificateMapper.selectByCertNo(eq(100L), anyString(), anyString())).thenReturn(null);
        when(personnelCertificateMapper.insert(any(PersonnelCertificateDO.class))).thenAnswer(inv -> {
            PersonnelCertificateDO d = inv.getArgument(0);
            d.setId(900L);
            return 1;
        });

        Long id = service.createCertificate(req("REG_PRACTICE_CERT", "610000000001", LocalDate.now().plusYears(3)));

        assertEquals(900L, id);
        ArgumentCaptor<PersonnelCertificateDO> cap = ArgumentCaptor.forClass(PersonnelCertificateDO.class);
        verify(personnelCertificateMapper).insert(cap.capture());
        assertEquals(PersonnelCertStatusEnum.VALID.name(), cap.getValue().getCertStatus());
        assertEquals(Boolean.TRUE, cap.getValue().getIsCurrent());
    }

    @Test
    void create_rejectsDuplicateCertNo_forSamePersonAndType() {
        when(personnelProfileMapper.selectById(100L)).thenReturn(profile(100L, 1L));
        when(personnelCertificateMapper.selectByCertNo(eq(100L), anyString(), anyString()))
                .thenReturn(new PersonnelCertificateDO());

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.createCertificate(req("REG_PRACTICE_CERT", "610000000001", LocalDate.now().plusYears(3))));
        assertEquals(ErrorCodeConstants.PERSONNEL_CERT_NO_DUPLICATE.getCode(), ex.getCode());
        verify(personnelCertificateMapper, never()).insert(any(PersonnelCertificateDO.class));
    }

    @Test
    void create_rejectsMissingValidTo_forExpiryRequiredType() {
        // 为什么必须有这条：没有有效期就没有预警依据，"到期提醒"功能会静默失效
        when(personnelProfileMapper.selectById(100L)).thenReturn(profile(100L, 1L));
        when(personnelCertificateMapper.selectByCertNo(eq(100L), anyString(), anyString())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.createCertificate(req("REG_PRACTICE_CERT", "610000000001", null)));
        assertEquals(ErrorCodeConstants.PERSONNEL_CERT_EXPIRY_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void create_marksExpired_whenValidToInPast() {
        when(personnelProfileMapper.selectById(100L)).thenReturn(profile(100L, 1L));
        when(personnelCertificateMapper.selectByCertNo(eq(100L), anyString(), anyString())).thenReturn(null);
        when(personnelCertificateMapper.insert(any(PersonnelCertificateDO.class))).thenAnswer(inv -> {
            PersonnelCertificateDO d = inv.getArgument(0);
            d.setId(901L);
            return 1;
        });

        service.createCertificate(req("REG_PRACTICE_CERT", "610000000002", LocalDate.now().minusDays(1)));

        ArgumentCaptor<PersonnelCertificateDO> cap = ArgumentCaptor.forClass(PersonnelCertificateDO.class);
        verify(personnelCertificateMapper).insert(cap.capture());
        assertEquals(PersonnelCertStatusEnum.EXPIRED.name(), cap.getValue().getCertStatus());
    }

    @Test
    void create_rejectsEntMismatch_withProfile() {
        // 证书挂 A 企业、人属 B 企业 = 数据错配，会污染后续一致性核验，必须拦住
        when(personnelProfileMapper.selectById(100L)).thenReturn(profile(100L, 2L));

        assertThrows(ServiceException.class, () ->
                service.createCertificate(req("REG_PRACTICE_CERT", "610000000001", LocalDate.now().plusYears(3))));
        verify(personnelCertificateMapper, never()).insert(any(PersonnelCertificateDO.class));
    }

    @Test
    void changeStatus_rejectsExpiringSoonSetByHuman() {
        // EXPIRING_SOON 是预警扫描的派生结果，人工设置会绕过预警口径
        PersonnelCertificateDO cert = new PersonnelCertificateDO();
        cert.setId(900L);
        cert.setCertStatus(PersonnelCertStatusEnum.VALID.name());
        cert.setVersion(0);
        when(personnelCertificateMapper.selectById(900L)).thenReturn(cert);

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.changeStatus(900L, PersonnelCertStatusEnum.EXPIRING_SOON.name(), 0, "手工置位"));
        assertEquals(ErrorCodeConstants.PERSONNEL_CERT_STATUS_TRANSITION_INVALID.getCode(), ex.getCode());
    }

    @Test
    void changeStatus_rejectsStaleVersion() {
        PersonnelCertificateDO cert = new PersonnelCertificateDO();
        cert.setId(900L);
        cert.setCertStatus(PersonnelCertStatusEnum.VALID.name());
        cert.setVersion(3);
        when(personnelCertificateMapper.selectById(900L)).thenReturn(cert);

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.changeStatus(900L, PersonnelCertStatusEnum.REVOKED.name(), 2, "吊销"));
        assertEquals(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED.getCode(), ex.getCode());
    }

    @Test
    void changeStatus_ok_validToRevoked() {
        PersonnelCertificateDO cert = new PersonnelCertificateDO();
        cert.setId(900L);
        cert.setCertStatus(PersonnelCertStatusEnum.VALID.name());
        cert.setVersion(1);
        when(personnelCertificateMapper.selectById(900L)).thenReturn(cert);
        when(personnelCertificateMapper.updateById(any(PersonnelCertificateDO.class))).thenReturn(1);

        service.changeStatus(900L, PersonnelCertStatusEnum.REVOKED.name(), 1, "注册单位变更");

        ArgumentCaptor<PersonnelCertificateDO> cap = ArgumentCaptor.forClass(PersonnelCertificateDO.class);
        verify(personnelCertificateMapper).updateById(cap.capture());
        assertEquals(PersonnelCertStatusEnum.REVOKED.name(), cap.getValue().getCertStatus());
    }
}
