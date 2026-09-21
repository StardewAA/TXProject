package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningHandleReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateWarningDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateTypeMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateWarningMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateWarningNotifyStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateWarningStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateWarningTypeEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomString;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_WARNING_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_WARNING_STATUS_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HrmCertificateWarningServiceImpl} 的单元测试类
 *
 * @author OPENLAB BS
 */
@Import({HrmCertificateWarningServiceImpl.class, HrmCertificateTypeServiceImpl.class,
        HrmCertificateStatusCalculatorImpl.class})
public class HrmCertificateWarningServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmCertificateWarningServiceImpl certificateWarningService;
    @Resource
    private HrmCertificateWarningMapper certificateWarningMapper;
    @Resource
    private HrmEmployeeCertificateMapper certificateMapper;
    @Resource
    private HrmCertificateTypeMapper certificateTypeMapper;

    @MockitoBean
    private HrmEmployeeService employeeService;
    @MockitoBean
    private NotifyMessageSendApi notifyMessageSendApi;

    @Test
    public void testScanAndGenerateWarnings_generateExpiringAndReview() {
        // mock 数据：到期与复审都落在提前期 30 天内
        LocalDate today = LocalDate.of(2026, 9, 18);
        HrmCertificateTypeDO certificateType = insertCertificateType(true, 30);
        HrmEmployeeCertificateDO certificate = insertCertificate(certificateType.getId(),
                today.plusDays(10).atStartOfDay(), today.plusDays(5).atStartOfDay(),
                HrmCertificateStatusEnum.EXPIRING.getStatus());

        // 调用
        int createdCount = certificateWarningService.scanAndGenerateWarnings(today);

        // 断言：到期与复审各生成一条
        assertEquals(2, createdCount);
        HrmCertificateWarningDO expiring = certificateWarningMapper.selectByUniqueKey(certificate.getId(),
                HrmCertificateWarningTypeEnum.EXPIRING.getType(), today);
        assertNotNull(expiring);
        assertEquals(10, expiring.getRemainDays().intValue());
        assertEquals(HrmCertificateWarningStatusEnum.PENDING.getStatus(), expiring.getStatus());
        assertEquals(HrmCertificateWarningNotifyStatusEnum.PENDING.getStatus(), expiring.getNotifyStatus());
        assertEquals(0, expiring.getNotifyRetryCount().intValue());
        HrmCertificateWarningDO review = certificateWarningMapper.selectByUniqueKey(certificate.getId(),
                HrmCertificateWarningTypeEnum.REVIEW.getType(), today);
        assertNotNull(review);
        assertEquals(5, review.getRemainDays().intValue());
    }

    @Test
    public void testScanAndGenerateWarnings_deduplicateSameDay() {
        // mock 数据
        LocalDate today = LocalDate.of(2026, 9, 18);
        HrmCertificateTypeDO certificateType = insertCertificateType(true, 30);
        insertCertificate(certificateType.getId(), today.plusDays(10).atStartOfDay(),
                today.plusDays(5).atStartOfDay(), HrmCertificateStatusEnum.EXPIRING.getStatus());

        // 调用：同日重复扫描
        int firstCount = certificateWarningService.scanAndGenerateWarnings(today);
        int secondCount = certificateWarningService.scanAndGenerateWarnings(today);

        // 断言：按「证件 + 预警类型 + 预警日期」去重，第二次不再新增
        assertEquals(2, firstCount);
        assertEquals(0, secondCount);
        assertEquals(2L, certificateWarningMapper.selectCountByStatus(
                HrmCertificateWarningStatusEnum.PENDING.getStatus()).longValue());
    }

    @Test
    public void testScanAndGenerateWarnings_nextDayCreatesNewRecord() {
        // mock 数据
        LocalDate today = LocalDate.of(2026, 9, 18);
        HrmCertificateTypeDO certificateType = insertCertificateType(false, 30);
        insertCertificate(certificateType.getId(), today.plusDays(10).atStartOfDay(), null,
                HrmCertificateStatusEnum.EXPIRING.getStatus());

        // 调用：跨日扫描应生成新一天的预警
        assertEquals(1, certificateWarningService.scanAndGenerateWarnings(today));
        assertEquals(1, certificateWarningService.scanAndGenerateWarnings(today.plusDays(1)));
    }

    @Test
    public void testScanAndGenerateWarnings_beyondAdvanceDays() {
        // mock 数据：剩余 200 天，远超提前期
        LocalDate today = LocalDate.of(2026, 9, 18);
        HrmCertificateTypeDO certificateType = insertCertificateType(true, 30);
        insertCertificate(certificateType.getId(), today.plusDays(200).atStartOfDay(),
                today.plusDays(180).atStartOfDay(), HrmCertificateStatusEnum.VALID.getStatus());

        // 调用
        int createdCount = certificateWarningService.scanAndGenerateWarnings(today);

        // 断言
        assertEquals(0, createdCount);
    }

    @Test
    public void testScanAndGenerateWarnings_reviewSkippedWhenNotNeeded() {
        // mock 数据：证件类型不需要复审
        LocalDate today = LocalDate.of(2026, 9, 18);
        HrmCertificateTypeDO certificateType = insertCertificateType(false, 30);
        insertCertificate(certificateType.getId(), today.plusDays(10).atStartOfDay(),
                today.plusDays(5).atStartOfDay(), HrmCertificateStatusEnum.EXPIRING.getStatus());

        // 调用
        int createdCount = certificateWarningService.scanAndGenerateWarnings(today);

        // 断言：只生成到期预警
        assertEquals(1, createdCount);
    }

    @Test
    public void testScanAndGenerateWarnings_skipCancelled() {
        // mock 数据：已注销的证件不参与预警
        LocalDate today = LocalDate.of(2026, 9, 18);
        HrmCertificateTypeDO certificateType = insertCertificateType(true, 30);
        insertCertificate(certificateType.getId(), today.plusDays(10).atStartOfDay(),
                today.plusDays(5).atStartOfDay(), HrmCertificateStatusEnum.CANCELLED.getStatus());

        // 调用
        assertEquals(0, certificateWarningService.scanAndGenerateWarnings(today));
    }

    @Test
    public void testHandleCertificateWarning_pendingToHandled() {
        // mock 数据
        HrmCertificateWarningDO warning = insertWarning(HrmCertificateWarningStatusEnum.PENDING.getStatus());
        // 准备参数
        HrmCertificateWarningHandleReqVO reqVO = buildHandleReqVO(warning.getId(),
                HrmCertificateWarningStatusEnum.HANDLED.getStatus()).setHandleRemark("已通知员工办理续证");

        // 调用
        certificateWarningService.handleCertificateWarning(reqVO);

        // 断言
        HrmCertificateWarningDO updated = certificateWarningMapper.selectById(warning.getId());
        assertEquals(HrmCertificateWarningStatusEnum.HANDLED.getStatus(), updated.getStatus());
        assertEquals("已通知员工办理续证", updated.getHandleRemark());
        assertNotNull(updated.getHandleTime());
    }

    @Test
    public void testHandleCertificateWarning_reEnableClearsHandleInfo() {
        // mock 数据：已处理的预警
        HrmCertificateWarningDO warning = insertWarning(HrmCertificateWarningStatusEnum.HANDLED.getStatus());

        // 调用：重新启用为待处理
        certificateWarningService.handleCertificateWarning(buildHandleReqVO(warning.getId(),
                HrmCertificateWarningStatusEnum.PENDING.getStatus()));

        // 断言：处理人、处理时间与处理意见被清空
        HrmCertificateWarningDO updated = certificateWarningMapper.selectById(warning.getId());
        assertEquals(HrmCertificateWarningStatusEnum.PENDING.getStatus(), updated.getStatus());
        assertNull(updated.getHandleUserId());
        assertNull(updated.getHandleTime());
        assertNull(updated.getHandleRemark());
    }

    @Test
    public void testHandleCertificateWarning_sameStatusInvalid() {
        // mock 数据
        HrmCertificateWarningDO warning = insertWarning(HrmCertificateWarningStatusEnum.PENDING.getStatus());

        // 调用，并断言异常
        assertServiceException(() -> certificateWarningService.handleCertificateWarning(
                        buildHandleReqVO(warning.getId(), HrmCertificateWarningStatusEnum.PENDING.getStatus())),
                CERTIFICATE_WARNING_STATUS_INVALID);
    }

    @Test
    public void testHandleCertificateWarning_handledToIgnoredInvalid() {
        // mock 数据：已处理的预警不能直接改成已忽略，必须先重新启用
        HrmCertificateWarningDO warning = insertWarning(HrmCertificateWarningStatusEnum.HANDLED.getStatus());

        // 调用，并断言异常
        assertServiceException(() -> certificateWarningService.handleCertificateWarning(
                        buildHandleReqVO(warning.getId(), HrmCertificateWarningStatusEnum.IGNORED.getStatus())),
                CERTIFICATE_WARNING_STATUS_INVALID);
    }

    @Test
    public void testHandleCertificateWarning_notExists() {
        // 调用，并断言异常
        assertServiceException(() -> certificateWarningService.handleCertificateWarning(
                        buildHandleReqVO(randomLongId(), HrmCertificateWarningStatusEnum.HANDLED.getStatus())),
                CERTIFICATE_WARNING_NOT_EXISTS);
    }

    @Test
    public void testGetPendingWarningCountAndMap() {
        // mock 数据
        HrmEmployeeCertificateDO first = insertCertificate(null, LocalDateTime.now().plusDays(10), null,
                HrmCertificateStatusEnum.EXPIRING.getStatus());
        HrmEmployeeCertificateDO second = insertCertificate(null, LocalDateTime.now().plusDays(20), null,
                HrmCertificateStatusEnum.EXPIRING.getStatus());
        insertWarning(first.getId(), HrmCertificateWarningStatusEnum.PENDING.getStatus());
        insertWarning(first.getId(), HrmCertificateWarningStatusEnum.PENDING.getStatus());
        insertWarning(first.getId(), HrmCertificateWarningStatusEnum.HANDLED.getStatus());
        insertWarning(second.getId(), HrmCertificateWarningStatusEnum.PENDING.getStatus());

        // 调用
        assertEquals(3L, certificateWarningService.getPendingWarningCount().longValue());
        Map<Long, Long> countMap = certificateWarningService.getPendingWarningCountMap(
                java.util.Arrays.asList(first.getId(), second.getId()));
        // 断言：只统计待处理状态
        assertEquals(2L, countMap.get(first.getId()).longValue());
        assertEquals(1L, countMap.get(second.getId()).longValue());
    }

    @Test
    public void testGetCertificateWarningPage_enrichFields() {
        // mock 数据
        HrmCertificateTypeDO certificateType = insertCertificateType(true, 30);
        HrmEmployeeCertificateDO certificate = insertCertificate(certificateType.getId(),
                LocalDateTime.now().plusDays(10), null, HrmCertificateStatusEnum.EXPIRING.getStatus());
        insertWarning(certificate.getId(), certificate.getEmployeeId(),
                HrmCertificateWarningStatusEnum.PENDING.getStatus());
        when(employeeService.getEmployeeListByIds(any())).thenReturn(Collections.singletonList(
                HrmEmployeeDO.builder().id(certificate.getEmployeeId()).name("张三")
                        .jobNumber("ADMIN-001").deptId(103L).build()));
        // 准备参数
        HrmCertificateWarningPageReqVO reqVO = new HrmCertificateWarningPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);

        // 调用
        PageResult<HrmCertificateWarningRespVO> pageResult =
                certificateWarningService.getCertificateWarningPage(reqVO);

        // 断言
        assertEquals(1, pageResult.getTotal());
        HrmCertificateWarningRespVO respVO = pageResult.getList().get(0);
        assertEquals("张三", respVO.getEmployeeName());
        assertEquals("ADMIN-001", respVO.getEmployeeJobNumber());
        assertEquals(certificate.getName(), respVO.getCertificateName());
        assertEquals(certificate.getNo(), respVO.getCertificateNo());
        assertEquals(certificateType.getName(), respVO.getCertificateTypeName());
    }

    @Test
    public void testNotifyPendingWarnings_employeeWithoutAccount() {
        // mock 数据：员工未绑定后台账号，通知失败并累计重试次数
        HrmEmployeeCertificateDO certificate = insertCertificate(null, LocalDateTime.now().plusDays(10), null,
                HrmCertificateStatusEnum.EXPIRING.getStatus());
        HrmCertificateWarningDO warning = insertWarning(certificate.getId(), certificate.getEmployeeId(),
                HrmCertificateWarningStatusEnum.PENDING.getStatus());
        when(employeeService.getEmployeeListByIds(any())).thenReturn(Collections.singletonList(
                HrmEmployeeDO.builder().id(certificate.getEmployeeId()).name("张三").build()));

        // 调用
        int successCount = certificateWarningService.notifyPendingWarnings(LocalDate.now());

        // 断言
        assertEquals(0, successCount);
        HrmCertificateWarningDO updated = certificateWarningMapper.selectById(warning.getId());
        assertEquals(HrmCertificateWarningNotifyStatusEnum.FAILED.getStatus(), updated.getNotifyStatus());
        assertNotNull(updated.getNotifyFailReason());
        assertEquals(1, updated.getNotifyRetryCount().intValue());
    }

    @Test
    public void testNotifyPendingWarnings_success() {
        // mock 数据：员工已绑定账号
        HrmEmployeeCertificateDO certificate = insertCertificate(null, LocalDateTime.now().plusDays(10), null,
                HrmCertificateStatusEnum.EXPIRING.getStatus());
        HrmCertificateWarningDO warning = insertWarning(certificate.getId(), certificate.getEmployeeId(),
                HrmCertificateWarningStatusEnum.PENDING.getStatus());
        when(employeeService.getEmployeeListByIds(any())).thenReturn(Collections.singletonList(
                HrmEmployeeDO.builder().id(certificate.getEmployeeId()).name("张三").userId(1L).build()));

        // 调用
        int successCount = certificateWarningService.notifyPendingWarnings(LocalDate.now());

        // 断言
        assertEquals(1, successCount);
        verify(notifyMessageSendApi).sendSingleMessageToAdmin(any());
        HrmCertificateWarningDO updated = certificateWarningMapper.selectById(warning.getId());
        assertEquals(HrmCertificateWarningNotifyStatusEnum.SUCCESS.getStatus(), updated.getNotifyStatus());
        assertNotNull(updated.getNotifyTime());
        assertNull(updated.getNotifyFailReason());
    }

    private HrmCertificateTypeDO insertCertificateType(boolean needReview, Integer advanceDays) {
        HrmCertificateTypeDO certificateType = HrmCertificateTypeDO.builder()
                .name("特种作业操作证" + randomString())
                .code(randomString())
                .specialOperation(needReview)
                .needReview(needReview)
                .reviewCycleMonths(needReview ? 36 : null)
                .advanceDays(advanceDays)
                .requireAttachment(false)
                .status(CommonStatusEnum.ENABLE.getStatus())
                .sort(10)
                .build();
        certificateTypeMapper.insert(certificateType);
        return certificateType;
    }

    private HrmEmployeeCertificateDO insertCertificate(Long typeId, LocalDateTime endTime,
                                                      LocalDateTime reviewTime, Integer status) {
        HrmEmployeeCertificateDO certificate = HrmEmployeeCertificateDO.builder()
                .employeeId(randomLongId())
                .typeId(typeId)
                .name("低压电工作业操作证")
                .no("T44030019900718001X")
                .startTime(LocalDateTime.now().minusYears(1))
                .endTime(endTime)
                .reviewTime(reviewTime)
                .status(status)
                .sort(1)
                .build();
        certificateMapper.insert(certificate);
        return certificate;
    }

    private HrmCertificateWarningDO insertWarning(Integer status) {
        return insertWarning(randomLongId(), randomLongId(), status);
    }

    private HrmCertificateWarningDO insertWarning(Long certificateId, Integer status) {
        return insertWarning(certificateId, randomLongId(), status);
    }

    private HrmCertificateWarningDO insertWarning(Long certificateId, Long employeeId, Integer status) {
        HrmCertificateWarningDO warning = HrmCertificateWarningDO.builder()
                .certificateId(certificateId)
                .employeeId(employeeId)
                .type(HrmCertificateWarningTypeEnum.EXPIRING.getType())
                .warningDate(LocalDate.of(2026, 9, 18))
                .expireDate(LocalDateTime.now().plusDays(10))
                .remainDays(10)
                .status(status)
                .notifyStatus(HrmCertificateWarningNotifyStatusEnum.PENDING.getStatus())
                .notifyRetryCount(0)
                .handleUserId(status.equals(HrmCertificateWarningStatusEnum.HANDLED.getStatus()) ? 1L : null)
                .handleTime(status.equals(HrmCertificateWarningStatusEnum.HANDLED.getStatus())
                        ? LocalDateTime.now() : null)
                .handleRemark(status.equals(HrmCertificateWarningStatusEnum.HANDLED.getStatus())
                        ? "历史处理意见" : null)
                .build();
        certificateWarningMapper.insert(warning);
        return warning;
    }

    private HrmCertificateWarningHandleReqVO buildHandleReqVO(Long id, Integer status) {
        HrmCertificateWarningHandleReqVO reqVO = new HrmCertificateWarningHandleReqVO();
        reqVO.setId(id);
        reqVO.setStatus(status);
        return reqVO;
    }

}
