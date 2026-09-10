package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.enums.SafetyPermitStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 安全生产许可证服务规则测试（Mockito，TDD）：建档默认 VALID/UK 判重/影像存在校验/状态机与 CAS。
 * 依据：《电子证件全流程管理_数据库设计.md》v3.3 表 15-8。
 */
class QualificationSafetyPermitServiceImplTest {

    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    private CertificateInfoMapper certificateInfoMapper;
    private QualificationSafetyPermitServiceImpl service;

    @BeforeEach
    void setUp() {
        qualificationSafetyPermitMapper = mock(QualificationSafetyPermitMapper.class);
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        service = new QualificationSafetyPermitServiceImpl();
        ReflectionTestUtils.setField(service, "qualificationSafetyPermitMapper", qualificationSafetyPermitMapper);
        ReflectionTestUtils.setField(service, "certificateInfoMapper", certificateInfoMapper);
    }

    @Test
    void createPermit_ok_defaultValid() {
        when(qualificationSafetyPermitMapper.selectByPermitNo("(陕)JZ安许证字[2023]000001")).thenReturn(null);
        when(qualificationSafetyPermitMapper.insert(any(QualificationSafetyPermitDO.class))).thenAnswer(inv -> {
            QualificationSafetyPermitDO p = inv.getArgument(0);
            p.setId(11L);
            return 1;
        });
        Long id = service.createPermit(1L, "(陕)JZ安许证字[2023]000001", "陕西省住房和城乡建设厅",
                LocalDate.of(2023, 6, 1), LocalDate.of(2026, 5, 31), "张**", null, null);
        assertEquals(11L, id);
        ArgumentCaptor<QualificationSafetyPermitDO> cap = ArgumentCaptor.forClass(QualificationSafetyPermitDO.class);
        verify(qualificationSafetyPermitMapper).insert(cap.capture());
        assertEquals(SafetyPermitStatusEnum.VALID.name(), cap.getValue().getPermitStatus());
    }

    @Test
    void createPermit_duplicatePermitNoRejected() {
        when(qualificationSafetyPermitMapper.selectByPermitNo("X")).thenReturn(new QualificationSafetyPermitDO());
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.createPermit(1L, "X", "a", null, null, null, null, null));
        assertEquals(Integer.valueOf(1_060_106_025), e.getCode());
        verify(qualificationSafetyPermitMapper, never()).insert(any(QualificationSafetyPermitDO.class));
    }

    @Test
    void createPermit_bindedCertMissingRejected() {
        when(qualificationSafetyPermitMapper.selectByPermitNo("X")).thenReturn(null);
        when(certificateInfoMapper.selectById(5L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.createPermit(1L, "X", "a", null, null, null, 5L, null));
        assertEquals(Integer.valueOf(1_060_106_000), e.getCode());
    }

    @Test
    void changeStatus_validToExpiringSoon_ok() {
        QualificationSafetyPermitDO p = permitOf(SafetyPermitStatusEnum.VALID);
        p.setVersion(3);
        when(qualificationSafetyPermitMapper.selectById(1L)).thenReturn(p);
        when(qualificationSafetyPermitMapper.updateById(any(QualificationSafetyPermitDO.class))).thenReturn(1);
        service.changeStatus(1L, SafetyPermitStatusEnum.EXPIRING_SOON.name(), 3, "预警扫描联动");
        assertEquals(SafetyPermitStatusEnum.EXPIRING_SOON.name(), p.getPermitStatus());
        assertEquals("预警扫描联动", p.getRemark());
    }

    @Test
    void changeStatus_revokedTerminalRejected() {
        QualificationSafetyPermitDO p = permitOf(SafetyPermitStatusEnum.REVOKED);
        p.setVersion(0);
        when(qualificationSafetyPermitMapper.selectById(1L)).thenReturn(p);
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.changeStatus(1L, SafetyPermitStatusEnum.VALID.name(), 0, null));
        assertEquals(Integer.valueOf(1_060_106_026), e.getCode());
        verify(qualificationSafetyPermitMapper, never()).updateById(any(QualificationSafetyPermitDO.class));
    }

    @Test
    void changeStatus_versionMismatchRejected() {
        QualificationSafetyPermitDO p = permitOf(SafetyPermitStatusEnum.VALID);
        p.setVersion(5);
        when(qualificationSafetyPermitMapper.selectById(1L)).thenReturn(p);
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.changeStatus(1L, SafetyPermitStatusEnum.EXPIRED.name(), 2, null));
        assertEquals(Integer.valueOf(1_060_106_026), e.getCode());
    }

    private QualificationSafetyPermitDO permitOf(SafetyPermitStatusEnum status) {
        QualificationSafetyPermitDO p = new QualificationSafetyPermitDO();
        p.setId(1L);
        p.setEntId(1L);
        p.setPermitNo("(陕)JZ安许证字[2023]000001");
        p.setPermitStatus(status.name());
        return p;
    }
}
