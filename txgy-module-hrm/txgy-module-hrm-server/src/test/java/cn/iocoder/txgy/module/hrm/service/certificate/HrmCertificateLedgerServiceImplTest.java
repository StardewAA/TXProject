package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateTypeMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomString;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_ATTACHMENT_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_END_TIME_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_STATUS_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HrmCertificateLedgerServiceImpl} 的单元测试类
 *
 * @author OPENLAB BS
 */
@Import({HrmCertificateLedgerServiceImpl.class, HrmCertificateTypeServiceImpl.class,
        HrmCertificateStatusCalculatorImpl.class})
public class HrmCertificateLedgerServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmCertificateLedgerServiceImpl certificateLedgerService;
    @Resource
    private HrmEmployeeCertificateMapper certificateMapper;
    @Resource
    private HrmCertificateTypeMapper certificateTypeMapper;

    @MockitoBean
    private HrmEmployeeService employeeService;
    @MockitoBean
    private HrmCertificateWarningService certificateWarningService;

    @Test
    public void testCreateCertificateLedger_success() {
        // mock 数据
        HrmCertificateTypeDO certificateType = insertCertificateType(true, false, 30);
        Long employeeId = randomLongId();
        // 准备参数：剩余 100 天，超过提前期，应为有效
        HrmCertificateLedgerSaveReqVO reqVO = buildReqVO(employeeId, certificateType.getId(),
                LocalDate.now().plusDays(100));

        // 调用
        Long id = certificateLedgerService.createCertificateLedger(reqVO);

        // 断言
        assertNotNull(id);
        HrmEmployeeCertificateDO certificate = certificateMapper.selectById(id);
        assertPojoEquals(reqVO, certificate, "id", "startTime", "endTime", "reviewTime", "issuingTime");
        assertEquals(HrmCertificateStatusEnum.VALID.getStatus(), certificate.getStatus());
        assertEquals(reqVO.getStartTime().toLocalDate().atStartOfDay(), certificate.getStartTime());
        assertEquals(reqVO.getEndTime().toLocalDate().atStartOfDay(), certificate.getEndTime());
        verify(employeeService).validateEmployeeExists(employeeId);
    }

    @Test
    public void testCreateCertificateLedger_statusExpiring() {
        // mock 数据
        HrmCertificateTypeDO certificateType = insertCertificateType(false, false, 30);
        // 准备参数：剩余 10 天，落在提前期内
        HrmCertificateLedgerSaveReqVO reqVO = buildReqVO(randomLongId(), certificateType.getId(),
                LocalDate.now().plusDays(10));

        // 调用
        Long id = certificateLedgerService.createCertificateLedger(reqVO);

        // 断言
        assertEquals(HrmCertificateStatusEnum.EXPIRING.getStatus(),
                certificateMapper.selectById(id).getStatus());
    }

    @Test
    public void testCreateCertificateLedger_endTimeBeforeStartTime() {
        // mock 数据
        HrmCertificateTypeDO certificateType = insertCertificateType(false, false, 30);
        // 准备参数：结束日期早于开始日期
        HrmCertificateLedgerSaveReqVO reqVO = buildReqVO(randomLongId(), certificateType.getId(),
                LocalDate.now().plusDays(10));
        reqVO.setStartTime(LocalDateTime.now());
        reqVO.setEndTime(LocalDateTime.now().minusDays(1));

        // 调用，并断言异常
        assertServiceException(() -> certificateLedgerService.createCertificateLedger(reqVO),
                CERTIFICATE_LEDGER_END_TIME_INVALID);
    }

    @Test
    public void testCreateCertificateLedger_attachmentRequired() {
        // mock 数据：证件类型要求必传附件
        HrmCertificateTypeDO certificateType = insertCertificateType(false, true, 30);
        // 准备参数：未传附件
        HrmCertificateLedgerSaveReqVO reqVO = buildReqVO(randomLongId(), certificateType.getId(),
                LocalDate.now().plusDays(100)).setAttachmentUrl(null);

        // 调用，并断言异常
        assertServiceException(() -> certificateLedgerService.createCertificateLedger(reqVO),
                CERTIFICATE_LEDGER_ATTACHMENT_REQUIRED, certificateType.getName());
    }

    @Test
    public void testCreateCertificateLedger_typeDisabled() {
        // mock 数据：证件类型已停用
        HrmCertificateTypeDO certificateType = insertCertificateType(false, false, 30);
        certificateTypeMapper.updateById(HrmCertificateTypeDO.builder()
                .id(certificateType.getId()).status(CommonStatusEnum.DISABLE.getStatus()).build());
        // 准备参数
        HrmCertificateLedgerSaveReqVO reqVO = buildReqVO(randomLongId(), certificateType.getId(),
                LocalDate.now().plusDays(100));

        // 调用，并断言异常
        assertServiceException(() -> certificateLedgerService.createCertificateLedger(reqVO),
                CERTIFICATE_TYPE_DISABLED, certificateType.getName());
    }

    @Test
    public void testUpdateCertificateLedger_success() {
        // mock 数据
        HrmCertificateTypeDO certificateType = insertCertificateType(false, false, 30);
        HrmEmployeeCertificateDO dbRecord = insertCertificate(randomLongId(), certificateType.getId(),
                LocalDateTime.now().plusDays(100), HrmCertificateStatusEnum.VALID.getStatus());
        // 准备参数：把有效期缩短到提前期内，状态应重算为即将到期
        HrmCertificateLedgerSaveReqVO reqVO = buildReqVO(dbRecord.getEmployeeId(), certificateType.getId(),
                LocalDate.now().plusDays(5)).setId(dbRecord.getId());

        // 调用
        certificateLedgerService.updateCertificateLedger(reqVO);

        // 断言
        HrmEmployeeCertificateDO certificate = certificateMapper.selectById(dbRecord.getId());
        assertPojoEquals(reqVO, certificate, "startTime", "endTime", "reviewTime", "issuingTime", "status");
        assertEquals(dbRecord.getEmployeeId(), certificate.getEmployeeId());
        assertEquals(HrmCertificateStatusEnum.EXPIRING.getStatus(), certificate.getStatus());
    }

    @Test
    public void testUpdateCertificateLedger_notExists() {
        // 准备参数
        HrmCertificateLedgerSaveReqVO reqVO = buildReqVO(randomLongId(), null, LocalDate.now().plusDays(100))
                .setId(randomLongId());

        // 调用，并断言异常
        assertServiceException(() -> certificateLedgerService.updateCertificateLedger(reqVO),
                CERTIFICATE_LEDGER_NOT_EXISTS);
    }

    @Test
    public void testDeleteCertificateLedger_success() {
        // mock 数据
        HrmEmployeeCertificateDO dbRecord = insertCertificate(randomLongId(), null,
                LocalDateTime.now().plusDays(100), HrmCertificateStatusEnum.VALID.getStatus());

        // 调用
        certificateLedgerService.deleteCertificateLedger(dbRecord.getId());

        // 断言
        assertNull(certificateMapper.selectById(dbRecord.getId()));
    }

    @Test
    public void testDeleteCertificateLedger_notExists() {
        // 调用，并断言异常
        assertServiceException(() -> certificateLedgerService.deleteCertificateLedger(randomLongId()),
                CERTIFICATE_LEDGER_NOT_EXISTS);
    }

    @Test
    public void testUpdateCertificateLedgerStatus_cancel() {
        // mock 数据
        HrmEmployeeCertificateDO dbRecord = insertCertificate(randomLongId(), null,
                LocalDateTime.now().plusDays(100), HrmCertificateStatusEnum.VALID.getStatus());

        // 调用
        certificateLedgerService.updateCertificateLedgerStatus(dbRecord.getId(),
                HrmCertificateStatusEnum.CANCELLED.getStatus());

        // 断言
        assertEquals(HrmCertificateStatusEnum.CANCELLED.getStatus(),
                certificateMapper.selectById(dbRecord.getId()).getStatus());
    }

    @Test
    public void testUpdateCertificateLedgerStatus_restoreRecalculates() {
        // mock 数据：已注销且已过期
        HrmCertificateTypeDO certificateType = insertCertificateType(false, false, 30);
        HrmEmployeeCertificateDO dbRecord = insertCertificate(randomLongId(), certificateType.getId(),
                LocalDateTime.now().minusDays(3), HrmCertificateStatusEnum.CANCELLED.getStatus());

        // 调用：恢复为有效，应按有效期重新计算为过期
        certificateLedgerService.updateCertificateLedgerStatus(dbRecord.getId(),
                HrmCertificateStatusEnum.VALID.getStatus());

        // 断言
        assertEquals(HrmCertificateStatusEnum.EXPIRED.getStatus(),
                certificateMapper.selectById(dbRecord.getId()).getStatus());
    }

    @Test
    public void testUpdateCertificateLedgerStatus_invalidTarget() {
        // mock 数据
        HrmEmployeeCertificateDO dbRecord = insertCertificate(randomLongId(), null,
                LocalDateTime.now().plusDays(100), HrmCertificateStatusEnum.VALID.getStatus());

        // 调用：只允许注销或恢复为有效
        assertServiceException(() -> certificateLedgerService.updateCertificateLedgerStatus(dbRecord.getId(),
                HrmCertificateStatusEnum.EXPIRED.getStatus()), CERTIFICATE_LEDGER_STATUS_INVALID);
    }

    @Test
    public void testRefreshCertificateStatus() {
        // mock 数据：一条状态已过期的证件，实际仍有效
        HrmCertificateTypeDO certificateType = insertCertificateType(false, false, 30);
        HrmEmployeeCertificateDO stale = insertCertificate(randomLongId(), certificateType.getId(),
                LocalDateTime.now().plusDays(100), HrmCertificateStatusEnum.EXPIRED.getStatus());
        // mock 数据：一条状态为有效，实际已过期
        HrmEmployeeCertificateDO expired = insertCertificate(randomLongId(), certificateType.getId(),
                LocalDateTime.now().minusDays(1), HrmCertificateStatusEnum.VALID.getStatus());

        // 调用
        int changedCount = certificateLedgerService.refreshCertificateStatus(LocalDate.now());

        // 断言
        assertEquals(2, changedCount);
        assertEquals(HrmCertificateStatusEnum.VALID.getStatus(),
                certificateMapper.selectById(stale.getId()).getStatus());
        assertEquals(HrmCertificateStatusEnum.EXPIRED.getStatus(),
                certificateMapper.selectById(expired.getId()).getStatus());
    }

    @Test
    public void testRefreshCertificateStatus_cancelledNotOverwritten() {
        // mock 数据：注销状态且已过期
        HrmEmployeeCertificateDO cancelled = insertCertificate(randomLongId(), null,
                LocalDateTime.now().minusDays(1), HrmCertificateStatusEnum.CANCELLED.getStatus());

        // 调用
        int changedCount = certificateLedgerService.refreshCertificateStatus(LocalDate.now());

        // 断言：注销是人工终态，不参与自动重算
        assertEquals(0, changedCount);
        assertEquals(HrmCertificateStatusEnum.CANCELLED.getStatus(),
                certificateMapper.selectById(cancelled.getId()).getStatus());
    }

    @Test
    public void testGetCertificateLedgerPage_enrichFields() {
        // mock 数据
        HrmCertificateTypeDO certificateType = insertCertificateType(true, false, 30);
        Long employeeId = 2048L;
        HrmEmployeeCertificateDO dbRecord = insertCertificate(employeeId, certificateType.getId(),
                LocalDateTime.now().plusDays(10), HrmCertificateStatusEnum.VALID.getStatus());
        // mock 员工与预警数量
        when(employeeService.getEmployeeListByIds(any())).thenReturn(Collections.singletonList(
                HrmEmployeeDO.builder().id(employeeId).name("张三").jobNumber("ADMIN-001").deptId(103L).build()));
        when(certificateWarningService.getPendingWarningCountMap(any()))
                .thenReturn(Collections.singletonMap(dbRecord.getId(), 2L));
        // 准备参数
        HrmCertificateLedgerPageReqVO reqVO = new HrmCertificateLedgerPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);

        // 调用
        PageResult<HrmCertificateLedgerRespVO> pageResult = certificateLedgerService.getCertificateLedgerPage(reqVO);

        // 断言
        assertEquals(1, pageResult.getTotal());
        HrmCertificateLedgerRespVO respVO = pageResult.getList().get(0);
        assertEquals("张三", respVO.getEmployeeName());
        assertEquals("ADMIN-001", respVO.getEmployeeJobNumber());
        assertEquals(103L, respVO.getDeptId().longValue());
        assertEquals(certificateType.getName(), respVO.getTypeName());
        assertEquals(Boolean.TRUE, respVO.getSpecialOperation());
        assertEquals(10, respVO.getRemainDays().intValue());
        assertEquals(2L, respVO.getPendingWarningCount().longValue());
    }

    @Test
    public void testGetCertificateLedgerRespVO_remainDaysNullForLongTerm() {
        // mock 数据：无有效结束日期
        HrmEmployeeCertificateDO dbRecord = insertCertificate(randomLongId(), null, null,
                HrmCertificateStatusEnum.VALID.getStatus());
        when(employeeService.getEmployeeListByIds(any())).thenReturn(Collections.emptyList());
        when(certificateWarningService.getPendingWarningCountMap(any())).thenReturn(Collections.emptyMap());

        // 调用
        HrmCertificateLedgerRespVO respVO = certificateLedgerService.getCertificateLedgerRespVO(dbRecord.getId());

        // 断言
        assertNotNull(respVO);
        assertNull(respVO.getRemainDays());
        assertEquals(0L, respVO.getPendingWarningCount().longValue());
    }

    @Test
    public void testValidateCertificateLedgerExists_notExists() {
        // 调用，并断言异常
        assertServiceException(() -> certificateLedgerService.validateCertificateLedgerExists(randomLongId()),
                CERTIFICATE_LEDGER_NOT_EXISTS);
    }

    private HrmCertificateTypeDO insertCertificateType(boolean specialOperation, boolean requireAttachment,
                                                       Integer advanceDays) {
        HrmCertificateTypeDO certificateType = HrmCertificateTypeDO.builder()
                .name("特种作业操作证" + randomString())
                .code(randomString())
                .specialOperation(specialOperation)
                .needReview(specialOperation)
                .reviewCycleMonths(specialOperation ? 36 : null)
                .advanceDays(advanceDays)
                .requireAttachment(requireAttachment)
                .status(CommonStatusEnum.ENABLE.getStatus())
                .sort(10)
                .build();
        certificateTypeMapper.insert(certificateType);
        return certificateType;
    }

    private HrmEmployeeCertificateDO insertCertificate(Long employeeId, Long typeId, LocalDateTime endTime,
                                                      Integer status) {
        HrmEmployeeCertificateDO certificate = HrmEmployeeCertificateDO.builder()
                .employeeId(employeeId)
                .typeId(typeId)
                .name("低压电工作业操作证")
                .no("T44030019900718001X")
                .startTime(LocalDateTime.now().minusYears(1))
                .endTime(endTime)
                .issuingAuthority("深圳市应急管理局")
                .status(status)
                .sort(1)
                .build();
        certificateMapper.insert(certificate);
        return certificate;
    }

    private HrmCertificateLedgerSaveReqVO buildReqVO(Long employeeId, Long typeId, LocalDate endDate) {
        HrmCertificateLedgerSaveReqVO reqVO = randomPojo(HrmCertificateLedgerSaveReqVO.class, o -> {
            o.setId(null);
            o.setEmployeeId(employeeId);
            o.setTypeId(typeId);
            o.setName("低压电工作业操作证");
            o.setNo("T44030019900718001X");
            o.setLevel("初级");
            o.setStartTime(LocalDateTime.now().minusYears(1));
            o.setEndTime(endDate.atStartOfDay());
            o.setReviewTime(null);
            o.setIssuingAuthority("深圳市应急管理局");
            o.setIssuingTime(LocalDateTime.now().minusYears(1));
            o.setAttachmentUrl("https://file.example.com/cert.pdf");
            o.setSort(1);
        });
        return reqVO;
    }

}
